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

package com.toshi.view.activity

import android.Manifest
import android.app.Activity
import android.arch.lifecycle.ViewModelProviders
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.support.v4.app.Fragment
import android.support.v4.content.FileProvider
import android.support.v7.app.AppCompatActivity
import com.toshi.BuildConfig
import com.toshi.R
import com.toshi.exception.PermissionException
import com.toshi.extensions.startActivityAndFinish
import com.toshi.model.local.Conversation
import com.toshi.model.local.User
import com.toshi.util.FileUtil
import com.toshi.util.PermissionUtil
import com.toshi.view.BaseApplication
import com.toshi.view.fragment.DialogFragment.ChooserDialog
import com.toshi.view.fragment.newconversation.GroupParticipantsFragment
import com.toshi.view.fragment.newconversation.GroupSetupFragment
import com.toshi.view.fragment.newconversation.UserParticipantsFragment
import com.toshi.viewModel.NewConversationViewModel
import java.io.File

class ConversationSetupActivity : AppCompatActivity() {

    companion object {
        private val PICK_IMAGE = 1
        private val CAPTURE_IMAGE = 2
        private val INTENT_TYPE = "image/*"
    }

    private val chooserDialog by lazy { ChooserDialog.newInstance() }
    private lateinit var viewModel: NewConversationViewModel
    private lateinit var capturedImagePath: String

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        init()
        if (savedInstanceState == null) openFragment(UserParticipantsFragment())
    }

    private fun init() {
        initViewModel()
        initView()
    }

    private fun initViewModel() {
        viewModel = ViewModelProviders.of(this).get(NewConversationViewModel::class.java)
    }

    private fun initView() = setContentView(R.layout.activity_new_conversation)

    private fun openFragment(fragment: Fragment) {
        supportFragmentManager
                .beginTransaction()
                .replace(R.id.container, fragment, fragment::class.java.canonicalName)
                .addToBackStack(fragment::class.java.canonicalName)
                .commit()
    }

    override fun onBackPressed() {
        if (supportFragmentManager.backStackEntryCount == 1) {
            finish()
        } else {
            supportFragmentManager.popBackStackImmediate()
        }
    }
    fun openConversation(user: User) {
        startActivityAndFinish<ChatActivity> {
            putExtra(ChatActivity.EXTRA__THREAD_ID, user.toshiId)
        }
    }

    fun openConversation(conversation: Conversation?) {
        conversation?.let {
            startActivityAndFinish<ChatActivity> {
                putExtra(ChatActivity.EXTRA__THREAD_ID, conversation.threadId)
            }
        }
    }

    fun openNewGroupFlow() = openFragment(GroupParticipantsFragment())

    fun openGroupSetupFlow(selectedParticipants: List<User>) = openFragment(GroupSetupFragment().setSelectedParticipants(selectedParticipants))

    fun showImageChooserDialog() {
        this.chooserDialog.setOnChooserClickListener(object : ChooserDialog.OnChooserClickListener {
            override fun captureImageClicked() = checkCameraPermission()
            override fun importImageFromGalleryClicked() = checkExternalStoragePermission()
        })
        this.chooserDialog.show(supportFragmentManager, ChooserDialog.TAG)
    }

    private fun checkExternalStoragePermission() {
        PermissionUtil.hasPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                PermissionUtil.READ_EXTERNAL_STORAGE_PERMISSION,
                { this.startGalleryActivity() }
        )
    }

    private fun checkCameraPermission() {
        PermissionUtil.hasPermission(
                this,
                Manifest.permission.CAMERA,
                PermissionUtil.CAMERA_PERMISSION,
                { this.startCameraActivity() }
        )
    }

    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<String>,
                                            grantResults: IntArray) {
        when (requestCode) {
            PermissionUtil.CAMERA_PERMISSION -> startCameraActivity()
            PermissionUtil.READ_EXTERNAL_STORAGE_PERMISSION -> startGalleryActivity()
            else -> throw PermissionException("This permission doesn't belong in this context")
        }
    }

    private fun startCameraActivity() {
        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (cameraIntent.resolveActivity(packageManager) != null) {
            val photoFile = FileUtil.createImageFileWithRandomName()
            this.capturedImagePath = photoFile.absolutePath
            val photoURI = FileProvider.getUriForFile(
                    BaseApplication.get(),
                    BuildConfig.APPLICATION_ID + FileUtil.FILE_PROVIDER_NAME,
                    photoFile)
            PermissionUtil.grantUriPermission(this, cameraIntent, photoURI)
            cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
            this.startActivityForResult(cameraIntent, CAPTURE_IMAGE)
        }
    }

    private fun startGalleryActivity() {
        val pickPictureIntent = Intent()
                .setType(INTENT_TYPE)
                .setAction(Intent.ACTION_GET_CONTENT)

        if (pickPictureIntent.resolveActivity(packageManager) != null) {
            val chooser = Intent.createChooser(
                    pickPictureIntent,
                    BaseApplication.get().getString(R.string.select_picture))
            startActivityForResult(chooser, PICK_IMAGE)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode != Activity.RESULT_OK) return

        val imageUri = when (requestCode) {
            CAPTURE_IMAGE -> Uri.fromFile(File(this.capturedImagePath))
            PICK_IMAGE -> data?.data
            else -> throw IllegalArgumentException("Unknown requestCode.")
        }

        val groupSetupFragment = supportFragmentManager.findFragmentByTag(GroupSetupFragment::class.java.canonicalName)
        (groupSetupFragment as GroupSetupFragment).avatarUri = imageUri
    }
}
