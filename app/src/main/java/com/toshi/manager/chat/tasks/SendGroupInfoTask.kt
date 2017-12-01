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

import com.toshi.exception.GroupCreationException
import com.toshi.model.local.Group
import com.toshi.model.local.User
import org.whispersystems.libsignal.util.Hex
import org.whispersystems.signalservice.api.SignalServiceMessageSender
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage
import org.whispersystems.signalservice.api.messages.SignalServiceGroup
import org.whispersystems.signalservice.api.push.SignalServiceAddress
import org.whispersystems.signalservice.api.push.exceptions.EncapsulatedExceptions
import java.io.IOException

class SendGroupInfoTask(
        private val signalMessageSender: SignalServiceMessageSender
) {
    fun run(requestingUsers: List<User>, group: Group) {
        for (requestingUser in requestingUsers) {
            val requestingUserAddress = requestingUser.toshiId
            run(requestingUserAddress, group)
        }
    }

    fun run(requestingUserAddress: String, group: Group) {
        if (!shouldSendGroupInfo(requestingUserAddress, group)) return
        try {
            val signalGroup = SignalServiceGroup.newBuilder(SignalServiceGroup.Type.UPDATE)
                    .withId(Hex.fromStringCondensed(group.id))
                    .withName(group.title)
                    .withMembers(group.memberIds)
                    .withAvatar(group.avatar.stream)
                    .build()
            val groupDataMessage = SignalServiceDataMessage.newBuilder()
                    .withTimestamp(System.currentTimeMillis())
                    .asGroupMessage(signalGroup)
                    .build()
            val address = SignalServiceAddress(requestingUserAddress)
            signalMessageSender.sendMessage(address, groupDataMessage)
        } catch (ex: Exception) {
            when (ex) {
                is IOException, is EncapsulatedExceptions -> throw GroupCreationException(ex)
                else -> throw ex
            }
        }
    }

    private fun shouldSendGroupInfo(requestingUserAddress: String, group: Group) = group.memberIds.contains(requestingUserAddress)
}