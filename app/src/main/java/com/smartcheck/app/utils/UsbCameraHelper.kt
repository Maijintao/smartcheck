package com.smartcheck.app.utils

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbManager
import timber.log.Timber
import java.io.File

/**
 * USB 摄像头辅助工具。
 * 支持通过 PID/VID 查找对应的 Camera2 cameraId，替代硬编码的 camera ID 绑定。
 */
object UsbCameraHelper {

    data class UsbCameraInfo(
        val cameraId: String,
        val vid: Int,
        val pid: Int
    )

    /**
     * 根据 VID/PID 查找对应的 camera ID。
     *
     * 查找顺序：
     * 1. 通过 sysfs 读取每个 camera 对应的 video 设备的 VID/PID 进行精确匹配。
     * 2. 若 sysfs 不可用，通过 UsbManager 枚举 USB 视频设备，结合外接摄像头进行启发式匹配。
     *
     * @param vid USB Vendor ID（如 0x046d）
     * @param pid USB Product ID（如 0x0825）
     * @return 匹配的 camera ID，未找到时返回 null
     */
    fun findCameraIdByVidPid(context: Context, vid: Int, pid: Int): String? {
        if (vid == 0 && pid == 0) return null

        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraIds = try {
            cameraManager.cameraIdList
        } catch (e: Exception) {
            Timber.e(e, "Failed to get camera id list")
            return null
        }

        // 方法1：尝试通过 sysfs 精确匹配
        for (cameraId in cameraIds) {
            val vidPid = getVidPidForCameraId(cameraId)
            if (vidPid != null && vidPid.first == vid && vidPid.second == pid) {
                Timber.i("Matched camera $cameraId by sysfs for VID=${formatHex(vid)} PID=${formatHex(pid)}")
                return cameraId
            }
        }

        // 方法2：UsbManager 启发式匹配
        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val usbVideoDevices = getUsbVideoDevices(usbManager)
        val matchingUsb = usbVideoDevices.find { it.vid == vid && it.pid == pid }
        if (matchingUsb != null) {
            // 优先找外接摄像头（LENS_FACING_EXTERNAL）
            val externalId = findExternalCamera(cameraManager, cameraIds)
            if (externalId != null) {
                Timber.i("Heuristic match: USB ${matchingUsb.deviceName} -> external camera $externalId")
                return externalId
            }
            // fallback：使用最后一个可用摄像头（通常 USB 摄像头排在内置之后）
            if (cameraIds.isNotEmpty()) {
                val fallbackId = cameraIds.last()
                Timber.w("Heuristic fallback: USB ${matchingUsb.deviceName} -> camera $fallbackId")
                return fallbackId
            }
        }

        Timber.w("No camera found for VID=${formatHex(vid)} PID=${formatHex(pid)}")
        return null
    }

    /**
     * 枚举所有摄像头及其对应的 USB VID/PID（如果可获取）。
     */
    fun getUsbCameraInfos(context: Context): List<UsbCameraInfo> {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val results = mutableListOf<UsbCameraInfo>()

        for (cameraId in cameraManager.cameraIdList) {
            val vidPid = getVidPidForCameraId(cameraId)
            results.add(UsbCameraInfo(
                cameraId = cameraId,
                vid = vidPid?.first ?: 0,
                pid = vidPid?.second ?: 0
            ))
        }
        return results
    }

    /**
     * 通过 sysfs 尝试获取指定 camera ID 对应的 USB VID/PID。
     *
     * 尝试多种 video 设备索引猜测模式（不同设备厂商的 camera ID 到 video 节点映射不同）。
     */
    private fun getVidPidForCameraId(cameraId: String): Pair<Int, Int>? {
        val videoIndices = guessVideoIndices(cameraId)

        for (videoIdx in videoIndices) {
            // 尝试多种 sysfs 路径模式
            val paths = listOf(
                "/sys/class/video4linux/video$videoIdx/device/idVendor" to
                    "/sys/class/video4linux/video$videoIdx/device/idProduct",
                "/sys/class/video4linux/video$videoIdx/device/../idVendor" to
                    "/sys/class/video4linux/video$videoIdx/device/../idProduct"
            )

            for ((vendorPath, productPath) in paths) {
                try {
                    val vendor = File(vendorPath).readText().trim()
                    val product = File(productPath).readText().trim()
                    val vid = vendor.toInt(16)
                    val pid = product.toInt(16)
                    if (vid != 0 || pid != 0) {
                        Timber.d("Camera $cameraId -> video$videoIdx VID=${formatHex(vid)} PID=${formatHex(pid)}")
                        return Pair(vid, pid)
                    }
                } catch (e: Exception) {
                    // 路径不存在或无权限，继续尝试
                }
            }
        }
        return null
    }

    /**
     * 猜测 camera ID 可能对应的 video 设备索引。
     *
     * 支持的映射模式：
     * - 直接映射：cameraId "0" -> video0
     * - 偏移映射：cameraId "109" -> video9（减去 100）
     * - 直接映射：cameraId "109" -> video109
     */
    private fun guessVideoIndices(cameraId: String): List<String> {
        val indices = mutableListOf<String>()

        // 模式1：camera ID 直接作为 video 索引
        indices.add(cameraId)

        // 模式2：数字 camera ID 且 >= 100，减去 100 作为偏移
        try {
            val num = cameraId.toInt()
            if (num >= 100) {
                indices.add((num - 100).toString())
            }
        } catch (_: NumberFormatException) {
            // ignore
        }

        return indices.distinct()
    }

    private fun getUsbVideoDevices(usbManager: UsbManager): List<UsbVideoDevice> {
        val devices = mutableListOf<UsbVideoDevice>()
        for (device in usbManager.deviceList.values) {
            val isVideoDevice = device.deviceClass == UsbConstants.USB_CLASS_VIDEO ||
                (0 until device.interfaceCount).any { i ->
                    val intf = device.getInterface(i)
                    intf.interfaceClass == UsbConstants.USB_CLASS_VIDEO
                }
            if (isVideoDevice) {
                devices.add(
                    UsbVideoDevice(
                        deviceName = device.deviceName,
                        vid = device.vendorId,
                        pid = device.productId
                    )
                )
            }
        }
        return devices
    }

    private fun findExternalCamera(cameraManager: CameraManager, cameraIds: Array<String>): String? {
        for (cameraId in cameraIds) {
            try {
                val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                val lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING)
                if (lensFacing == CameraCharacteristics.LENS_FACING_EXTERNAL) {
                    return cameraId
                }
            } catch (_: Exception) {
                // ignore
            }
        }
        return null
    }

    private data class UsbVideoDevice(
        val deviceName: String,
        val vid: Int,
        val pid: Int
    )

    /**
     * 解析 "VID:PID" 格式的字符串（十六进制）。
     * 支持 "046d:0825"、"0x046d:0x0825"、"46d:825" 等格式。
     */
    fun parseVidPid(vidPidString: String): Pair<Int, Int>? {
        val trimmed = vidPidString.trim()
        if (trimmed.isBlank()) return null

        val parts = trimmed.split(":", limit = 2)
        if (parts.size != 2) return null

        return try {
            val vid = parts[0].trim().removePrefix("0x").toInt(16)
            val pid = parts[1].trim().removePrefix("0x").toInt(16)
            Pair(vid, pid)
        } catch (_: NumberFormatException) {
            null
        }
    }

    /**
     * 将 VID/PID 格式化为 "VID:PID" 字符串（4位十六进制大写）。
     */
    fun formatVidPid(vid: Int, pid: Int): String {
        return String.format("%04X:%04X", vid, pid)
    }

    private fun formatHex(value: Int): String {
        return String.format("0x%04X", value)
    }

    /**
     * 扫描并返回所有已连接的 USB 视频设备列表（通过 UsbManager）。
     * 不需要 root 权限即可使用。
     */
    fun scanUsbVideoDevices(context: Context): List<UsbVideoDeviceInfo> {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val results = mutableListOf<UsbVideoDeviceInfo>()
        for (device in usbManager.deviceList.values) {
            val isVideoDevice = device.deviceClass == UsbConstants.USB_CLASS_VIDEO ||
                (0 until device.interfaceCount).any { i ->
                    val intf = device.getInterface(i)
                    intf.interfaceClass == UsbConstants.USB_CLASS_VIDEO
                }
            if (isVideoDevice) {
                results.add(
                    UsbVideoDeviceInfo(
                        deviceName = device.deviceName,
                        vid = device.vendorId,
                        pid = device.productId,
                        productName = device.productName,
                        manufacturerName = device.manufacturerName
                    )
                )
            }
        }
        return results
    }

    data class UsbVideoDeviceInfo(
        val deviceName: String,
        val vid: Int,
        val pid: Int,
        val productName: String?,
        val manufacturerName: String?
    )
}
