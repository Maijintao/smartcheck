package com.smartcheck.app.data.db

import androidx.room.*

@Dao
interface DeletedEmployeeVersionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: DeletedEmployeeVersionEntity)

    @Query("SELECT * FROM deleted_employee_versions WHERE employee_id = :employeeId")
    suspend fun getVersion(employeeId: String): DeletedEmployeeVersionEntity?

    @Query("DELETE FROM deleted_employee_versions WHERE employee_id = :employeeId")
    suspend fun delete(employeeId: String)

    @Query("DELETE FROM deleted_employee_versions")
    suspend fun deleteAll()
}
