/*
 * 	Copyright (c) 2017. Toshi Inc
 *
 * 	This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.toshi.manager.network.interceptor

import com.toshi.BuildConfig
import com.toshi.util.LogUtil
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.net.SocketTimeoutException

class AppInfoUserAgentInterceptor : Interceptor {

    private val userAgent: String = "Android " + BuildConfig.APPLICATION_ID + " - " + BuildConfig.VERSION_NAME + ":" + BuildConfig.VERSION_CODE

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response? {
        return try {
            val original = chain.request()
            val request = original.newBuilder()
                    .header("User-Agent", this.userAgent)
                    .method(original.method(), original.body())
                    .build()
            chain.proceed(request)
        } catch (ex: SocketTimeoutException) {
            LogUtil.exception(javaClass, "Error while intercepting request", ex)
            null
        }
    }
}
