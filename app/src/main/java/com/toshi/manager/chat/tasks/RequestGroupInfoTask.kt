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

package com.toshi.manager.chat.tasks

import com.toshi.model.local.User
import com.toshi.util.LogUtil
import org.whispersystems.signalservice.api.SignalServiceMessageSender
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage
import org.whispersystems.signalservice.api.messages.SignalServiceGroup
import org.whispersystems.signalservice.api.push.SignalServiceAddress
import java.io.IOException

class RequestGroupInfoTask(
        private val signalMessageSender: SignalServiceMessageSender
) {
    fun run(sender: User, group: SignalServiceGroup) {
        try {
            val signalGroup = SignalServiceGroup
                    .newBuilder(SignalServiceGroup.Type.REQUEST_INFO)
                    .withId(group.groupId)
                    .build()
            val dataMessage = SignalServiceDataMessage
                    .newBuilder()
                    .asGroupMessage(signalGroup)
                    .withTimestamp(System.currentTimeMillis())
                    .build()
            signalMessageSender.sendMessage(SignalServiceAddress(sender.toshiId), dataMessage)
        } catch (ex: Exception) {
            when (ex) {
                is IOException, is UntrustedIdentityException -> LogUtil.exception(javaClass, "Unable to request group info")
                else -> throw ex
            }
        }
    }
}