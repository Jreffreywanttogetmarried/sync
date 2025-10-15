package com.easydeals.sync.mapper;

import com.easydeals.sync.entity.SyncTask;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 同步任务Mapper
 */
@Mapper
public interface SyncTaskMapper {

    /**
     * 插入任务记录
     */
    @Insert("INSERT INTO sync_task (task_id, batch_id, website_code, data_type, business_id, sync_data, " +
            "status, retry_count, crm_request_code, error_msg, callback_status, created_at, updated_at) " +
            "VALUES (#{taskId}, #{batchId}, #{websiteCode}, #{dataType}, #{businessId}, #{syncData}, " +
            "#{status}, #{retryCount}, #{crmRequestCode}, #{errorMsg}, #{callbackStatus}, #{createdAt}, #{updatedAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(SyncTask syncTask);

    /**
     * 批量插入任务记录
     */
    @Insert("<script>" +
            "INSERT INTO sync_task (task_id, batch_id, website_code, data_type, business_id, sync_data, " +
            "status, retry_count, callback_status, created_at, updated_at) VALUES " +
            "<foreach collection='tasks' item='task' separator=','>" +
            "(#{task.taskId}, #{task.batchId}, #{task.websiteCode}, #{task.dataType}, #{task.businessId}, " +
            "#{task.syncData}, #{task.status}, #{task.retryCount}, #{task.callbackStatus}, #{task.createdAt}, #{task.updatedAt})" +
            "</foreach>" +
            "</script>")
    int batchInsert(@Param("tasks") List<SyncTask> tasks);

    /**
     * 根据任务ID查询
     */
    @Select("SELECT * FROM sync_task WHERE task_id = #{taskId}")
    SyncTask findByTaskId(String taskId);

    /**
     * 根据批次ID查询任务列表
     */
    @Select("SELECT * FROM sync_task WHERE batch_id = #{batchId} ORDER BY created_at ASC")
    List<SyncTask> findByBatchId(String batchId);

    /**
     * 根据网站代码和业务ID查询已成功的任务
     */
    @Select("SELECT * FROM sync_task WHERE website_code = #{websiteCode} AND business_id = #{businessId} AND status = #{status} AND data_type = #{dataType} LIMIT 1")
    SyncTask findByWebsiteCodeAndBusinessIdAndStatus(@Param("websiteCode") String websiteCode,
                                                     @Param("businessId") String businessId,
                                                     @Param("status") Integer status,
                                                     @Param("dataType") String dataType);

    /**
     * 根据状态查询任务列表
     */
    @Select("SELECT * FROM sync_task WHERE status = #{status} ORDER BY created_at ASC LIMIT #{limit}")
    List<SyncTask> findByStatusWithLimit(@Param("status") Integer status, @Param("limit") Integer limit);

    /**
     * 查询需要重试的失败任务
     */
    @Select("SELECT * FROM sync_task WHERE status = 3 AND retry_count < #{maxRetry} AND updated_at < #{beforeTime} ORDER BY updated_at ASC LIMIT #{limit}")
    List<SyncTask> findRetryableTasks(@Param("maxRetry") Integer maxRetry,
                                      @Param("beforeTime") LocalDateTime beforeTime,
                                      @Param("limit") Integer limit);

    /**
     * 查询有CRM请求编码但状态为处理中的任务
     */
    @Select("SELECT * FROM sync_task WHERE status = 1 AND crm_request_code IS NOT NULL ORDER BY updated_at ASC LIMIT #{limit}")
    List<SyncTask> findProcessingTasksWithRequestCode(@Param("limit") Integer limit);

    /**
     * 更新任务状态
     */
    @Update("UPDATE sync_task SET status = #{status}, updated_at = #{updatedAt} WHERE task_id = #{taskId}")
    int updateStatus(@Param("taskId") String taskId, @Param("status") Integer status, @Param("updatedAt") LocalDateTime updatedAt);

    /**
     * 更新任务CRM请求编码
     */
    @Update("UPDATE sync_task SET crm_request_code = #{crmRequestCode}, updated_at = #{updatedAt} WHERE task_id = #{taskId}")
    int updateCrmRequestCode(@Param("taskId") String taskId, @Param("crmRequestCode") String crmRequestCode, @Param("updatedAt") LocalDateTime updatedAt);

    /**
     * 更新延迟重试任务的CRM请求编码，同时将状态更新为PROCESSING并清除错误信息
     */
    @Update("UPDATE sync_task SET crm_request_code = #{crmRequestCode}, status = 1, error_msg = NULL, updated_at = #{updatedAt} WHERE task_id = #{taskId} AND status = 5")
    int updateDelayedRetryTaskCrmRequestCode(@Param("taskId") String taskId, @Param("crmRequestCode") String crmRequestCode, @Param("updatedAt") LocalDateTime updatedAt);

    /**
     * 更新任务为失败状态
     */
    @Update("UPDATE sync_task SET status = 3, error_msg = #{errorMsg}, retry_count = retry_count + 1, updated_at = #{updatedAt} WHERE task_id = #{taskId}")
    int updateToFailed(@Param("taskId") String taskId, @Param("errorMsg") String errorMsg, @Param("updatedAt") LocalDateTime updatedAt);

    /**
     * 更新任务为失败状态
     */
    @Update("UPDATE sync_task SET status = 3, error_msg = #{errorMsg}, retry_count = 3, updated_at = #{updatedAt} WHERE task_id = #{taskId}")
    int updateToFailedAfterReTry(@Param("taskId") String taskId, @Param("errorMsg") String errorMsg, @Param("updatedAt") LocalDateTime updatedAt);

    /**
     * 更新任务为成功状态
     */
    @Update("UPDATE sync_task SET status = 2, error_msg = NULL, updated_at = #{updatedAt} WHERE task_id = #{taskId}")
    int updateToSuccess(@Param("taskId") String taskId, @Param("updatedAt") LocalDateTime updatedAt);

    /**
     * 更新任务为延迟重试状态
     */
    @Update("UPDATE sync_task SET status = 5, error_msg = #{errorMsg}, retry_count = retry_count + 1, updated_at = #{updatedAt} WHERE task_id = #{taskId}")
    int updateToDelayedRetry(@Param("taskId") String taskId, @Param("errorMsg") String errorMsg, @Param("updatedAt") LocalDateTime updatedAt);

    /**
     * 根据状态统计任务数量
     */
    @Select("SELECT COUNT(*) FROM sync_task WHERE status = #{status}")
    long countByStatus(Integer status);

    /**
     * 根据批次ID统计各状态任务数量
     */
    @Select("SELECT status, COUNT(*) as count FROM sync_task WHERE batch_id = #{batchId} GROUP BY status")
    @Results({
            @Result(property = "status", column = "status"),
            @Result(property = "count", column = "count")
    })
    List<TaskStatusCount> countByBatchIdGroupByStatus(String batchId);

    /**
     * 根据网站代码和状态统计任务数量
     */
    @Select("SELECT COUNT(*) FROM sync_task WHERE website_code = #{websiteCode} AND status = #{status}")
    long countByWebsiteCodeAndStatus(@Param("websiteCode") String websiteCode, @Param("status") Integer status);

    /**
     * 查询指定时间范围内的任务
     */
    @Select("SELECT * FROM sync_task WHERE created_at BETWEEN #{startTime} AND #{endTime} ORDER BY created_at DESC LIMIT #{limit}")
    List<SyncTask> findByCreatedAtBetween(@Param("startTime") LocalDateTime startTime,
                                          @Param("endTime") LocalDateTime endTime,
                                          @Param("limit") Integer limit);

    /**
     * 删除批次下的所有任务
     */
    @Delete("DELETE FROM sync_task WHERE batch_id = #{batchId}")
    int deleteByBatchId(String batchId);

    /**
     * 查询需要回调的任务（成功或失败且未回调或回调失败的任务）
     */
    @Select("SELECT * FROM sync_task WHERE (status = 2 OR status = 3) AND (callback_status = 0 OR callback_status = 2) AND website_code = #{websiteCode} ORDER BY updated_at ASC LIMIT #{limit}")
    List<SyncTask> findTasksNeedCallback(@Param("websiteCode") String websiteCode, @Param("limit") Integer limit);

    /**
     * 更新回调状态
     */
    @Update("UPDATE sync_task SET callback_status = #{callbackStatus}, updated_at = #{updatedAt} WHERE task_id = #{taskId}")
    int updateCallbackStatus(@Param("taskId") String taskId, @Param("callbackStatus") Integer callbackStatus, @Param("updatedAt") LocalDateTime updatedAt);

    /**
     * 将延迟重试任务更新为处理中状态
     * 同时清除错误信息并保存CRM请求编码
     */
    @Update("UPDATE sync_task SET status = 2, error_msg = NULL, crm_request_code = #{crmRequestCode}, updated_at = NOW() " +
            "WHERE task_id = #{taskId} AND status = 5")
    int updateDelayedRetryTaskToSuc(@Param("taskId") String taskId, @Param("crmRequestCode") String crmRequestCode);

    /**
     * 任务状态统计结果类
     */
    class TaskStatusCount {
        private Integer status;
        private Long count;

        public Integer getStatus() { return status; }
        public void setStatus(Integer status) { this.status = status; }
        public Long getCount() { return count; }
        public void setCount(Long count) { this.count = count; }
    }
}