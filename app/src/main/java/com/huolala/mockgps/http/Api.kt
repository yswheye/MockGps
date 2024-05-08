package com.huolala.mockgps.http

import com.castiel.common.base.BaseResponse
import com.huolala.mockgps.model.AppUpdateModel
import retrofit2.http.FieldMap
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

/**
 * @author jiayu.liu
 */
interface Api {
    /**
     * 检测app版本
     */
    @POST("/apiv2/app/check")
    @FormUrlEncoded
    suspend fun checkAppUpdate(@FieldMap args: Map<String, String>): BaseResponse<AppUpdateModel>
}