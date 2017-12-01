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

package com.toshi.viewModel

import android.arch.lifecycle.ViewModel
import android.graphics.Bitmap
import android.net.Uri
import com.toshi.extensions.toIdenticon
import com.toshi.model.local.Conversation
import com.toshi.model.local.Group
import com.toshi.model.local.User
import com.toshi.util.ImageUtil
import com.toshi.util.SingleLiveEvent
import com.toshi.view.BaseApplication
import rx.Completable
import rx.Single
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import rx.subscriptions.CompositeSubscription

class GroupSetupViewModel : ViewModel() {

    private val subscriptions by lazy { CompositeSubscription() }
    private var isCreatingGroup = false

    val conversationCreated by lazy { SingleLiveEvent<Conversation>() }
    val groupUpdated by lazy { SingleLiveEvent<Group>() }
    val group by lazy { SingleLiveEvent<Group>() }
    val error by lazy { SingleLiveEvent<Throwable>() }

    fun createGroup(participants: List<User>,
                    avatarUri: Uri?,
                    groupName: String) {
        if (isCreatingGroup) return
        isCreatingGroup = true
        val subscription = generateAvatarFromUri(avatarUri)
                .map { avatar -> tryGeneratePlaceholderAvatar(avatar, groupName) }
                .map { avatar -> createGroupObject(participants, avatar, groupName) }
                .flatMap { addCurrentUserToGroup(it) }
                .flatMap { createConversationFromGroup(it) }
                .observeOn(AndroidSchedulers.mainThread())
                .doAfterTerminate { isCreatingGroup = false }
                .subscribe(
                        { conversationCreated.value = it },
                        { error.value = it }
                )
        this.subscriptions.add(subscription)
    }

    fun fetchGroup(groupId: String?) {
        val subscription = BaseApplication.get()
                .recipientManager
                .getGroupFromId(groupId)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        { group.value = it },
                        { error.value = it }
                )
        subscriptions.add(subscription)
    }

    fun updateGroup(group: Group,
                    avatarUri: Uri?,
                    groupName: String) {
        if (isCreatingGroup) return
        isCreatingGroup = true
        val subscription = generateAvatarFromUri(avatarUri)
                .map { avatar -> updateGroupObject(group, avatar, groupName) }
                .flatMapCompletable { saveUpdatedGroup(it) }
                .observeOn(AndroidSchedulers.mainThread())
                .doAfterTerminate { isCreatingGroup = false }
                .subscribe(
                        { groupUpdated.value = group },
                        { error.value = it }
                )
        this.subscriptions.add(subscription)
    }

    private fun createGroupObject(participants: List<User>,
                                  avatar: Bitmap?,
                                  groupName: String): Group {
        return Group(participants)
                .setTitle(groupName)
                .setAvatar(avatar)
    }

    private fun updateGroupObject(group: Group,
                                  newAvatar: Bitmap?,
                                  groupName: String): Group {
        newAvatar?.let { group.setAvatar(newAvatar) }
        group.title = groupName
        return group
    }

    private fun addCurrentUserToGroup(group: Group): Single<Group> {
        return BaseApplication
                .get()
                .userManager
                .currentUser
                .map { group.addMember(it) }
    }

    private fun createConversationFromGroup(group: Group): Single<Conversation> {
        return BaseApplication
                .get()
                .sofaMessageManager
                .createConversationFromGroup(group)
    }

    private fun saveUpdatedGroup(group: Group): Completable {
        return BaseApplication
                .get()
                .sofaMessageManager
                .updateConversationFromGroup(group)
    }

    private fun generateAvatarFromUri(avatarUri: Uri?) = ImageUtil.loadAsBitmap(avatarUri, BaseApplication.get())
    private fun tryGeneratePlaceholderAvatar(avatar: Bitmap?, groupName: String) = avatar.let { avatar } ?: groupName.toIdenticon()
    override fun onCleared() = subscriptions.clear()
}
