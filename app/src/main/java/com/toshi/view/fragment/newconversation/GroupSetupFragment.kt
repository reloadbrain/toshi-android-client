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

package com.toshi.view.fragment.newconversation

import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.net.Uri
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v7.widget.DefaultItemAnimator
import android.support.v7.widget.LinearLayoutManager
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.toshi.R
import com.toshi.extensions.addHorizontalLineDivider
import com.toshi.extensions.toast
import com.toshi.model.local.User
import com.toshi.util.ImageUtil
import com.toshi.util.LogUtil
import com.toshi.view.activity.ConversationSetupActivity
import com.toshi.view.adapter.GroupParticipantAdapter
import com.toshi.viewModel.GroupSetupViewModel
import kotlinx.android.synthetic.main.fragment_group_setup.*

class GroupSetupFragment : Fragment() {

    private lateinit var viewModel: GroupSetupViewModel
    private lateinit var selectedParticipants: List<User>
    private lateinit var userAdapter: GroupParticipantAdapter

    var avatarUri: Uri? = null
        set(value) { field = value; ImageUtil.renderFileIntoTarget(value, avatar) }

    fun setSelectedParticipants(selectedParticipants: List<User>): GroupSetupFragment {
        this.selectedParticipants = selectedParticipants
        return this
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater?.inflate(R.layout.fragment_group_setup, container, false)
    }

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) = init()

    private fun init() {
        initViewModel()
        initView()
        initClickListeners()
        initObservers()
    }

    private fun initViewModel() {
        viewModel = ViewModelProviders.of(this.activity).get(GroupSetupViewModel::class.java)
    }

    private fun initView() {
        initRecyclerView()
        initNumberOfParticipantsView()
        initAvatarPlaceholder()
    }

    private fun initClickListeners() {
        create.setOnClickListener { viewModel.createGroup(selectedParticipants, avatarUri, groupName.text.toString()) }
        closeButton.setOnClickListener { this.activity.onBackPressed() }
        avatar.setOnClickListener { (this.activity as ConversationSetupActivity).showImageChooserDialog() }
    }

    private fun initRecyclerView() {
        userAdapter = GroupParticipantAdapter().addUsers(this.selectedParticipants)
        participants.apply {
            layoutManager = LinearLayoutManager(this.context)
            itemAnimator = DefaultItemAnimator()
            adapter = userAdapter
            addHorizontalLineDivider()
        }
    }

    private fun initNumberOfParticipantsView() {
        val participantSize = this.selectedParticipants.size
        val participantsLabel = this.resources.getQuantityString(R.plurals.participants, participantSize, participantSize)
        numberOfParticipants.text = participantsLabel
    }

    private fun initAvatarPlaceholder() {
        avatar.setImageResource(R.drawable.ic_camera_with_background)
    }

    private fun initObservers() {
        initNameListener()
        viewModel.conversationCreated.observe(this, Observer {
            (this.activity as ConversationSetupActivity).openConversation(it)
        })
        viewModel.error.observe(this, Observer {
            LogUtil.exception(this::class.java, it)
            toast(R.string.error__group_creation, Toast.LENGTH_LONG)
        })
    }

    private fun initNameListener() {
        groupName.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable) { create.isEnabled = s.toString().isNotEmpty() }
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
        })
    }
}
