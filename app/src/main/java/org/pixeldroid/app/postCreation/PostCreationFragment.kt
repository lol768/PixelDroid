package org.pixeldroid.app.postCreation

import android.app.Activity
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toFile
import androidx.core.net.toUri
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import org.pixeldroid.app.R
import org.pixeldroid.app.databinding.FragmentPostCreationBinding
import org.pixeldroid.app.postCreation.camera.CameraActivity
import org.pixeldroid.app.postCreation.carousel.CarouselItem
import org.pixeldroid.app.utils.BaseFragment
import org.pixeldroid.app.utils.db.entities.InstanceDatabaseEntity
import org.pixeldroid.app.utils.db.entities.UserDatabaseEntity
import org.pixeldroid.app.utils.fileExtension
import org.pixeldroid.app.utils.getMimeType
import org.pixeldroid.media_editor.photoEdit.PhotoEditActivity
import org.pixeldroid.media_editor.photoEdit.VideoEditActivity
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Locale


class PostCreationFragment : BaseFragment() {

    private var user: UserDatabaseEntity? = null
    private var instance: InstanceDatabaseEntity = InstanceDatabaseEntity("", "")

    private lateinit var binding: FragmentPostCreationBinding
    private lateinit var model: PostCreationViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        super.onCreateView(inflater, container, savedInstanceState)

        // Inflate the layout for this fragment
        binding = FragmentPostCreationBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        user = db.userDao().getActiveUser()

        instance = user?.run {
            db.instanceDao().getAll().first { instanceDatabaseEntity ->
                instanceDatabaseEntity.uri.contains(instance_uri)
            }
        } ?: InstanceDatabaseEntity("", "")

        val _model: PostCreationViewModel by activityViewModels {
            PostCreationViewModelFactory(
                requireActivity().application,
                requireActivity().intent.clipData!!,
                instance,
                requireActivity().intent.getStringExtra(PostCreationActivity.PICTURE_DESCRIPTION),
                requireActivity().intent.getBooleanExtra(PostCreationActivity.POST_NSFW, false)
            )
        }
        model = _model

        model.getPhotoData().observe(viewLifecycleOwner) { newPhotoData ->
            // update UI
            binding.carousel.addData(
                newPhotoData.map {
                    CarouselItem(
                        it.imageUri, it.imageDescription, it.video,
                        it.videoEncodeProgress, it.videoEncodeStabilizationFirstPass,
                        it.videoEncodeComplete, it.videoEncodeError,
                    )
                }
            )
        }

        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.uiState.collect { uiState ->
                    uiState.userMessage?.let {
                        MaterialAlertDialogBuilder(binding.root.context).apply {
                            setMessage(it)
                            setNegativeButton(android.R.string.ok) { _, _ -> }
                        }.show()

                        // Notify the ViewModel the message is displayed
                        model.userMessageShown()
                    }
                    binding.addPhotoButton.isEnabled = uiState.addPhotoButtonEnabled
                    binding.removePhotoButton.isEnabled = uiState.removePhotoButtonEnabled
                    binding.editPhotoButton.isEnabled = uiState.editPhotoButtonEnabled
                    binding.toolbarPostCreation.visibility =
                        if (uiState.isCarousel) View.VISIBLE else View.INVISIBLE
                    binding.carousel.layoutCarousel = uiState.isCarousel
                }
            }
        }

        binding.carousel.apply {
            layoutCarouselCallback = { model.becameCarousel(it)}
            maxEntries = instance.albumLimit
            addPhotoButtonCallback = {
                addPhoto()
            }
            updateDescriptionCallback = { position: Int, description: String ->
                model.updateDescription(position, description)
            }
        }
        // get the description and send the post
        binding.postCreationSendButton.setOnClickListener {
            if (validatePost() && model.isNotEmpty()) {
                findNavController().navigate(R.id.action_postCreationFragment_to_postSubmissionFragment)
            }
        }

        binding.editPhotoButton.setOnClickListener {
            binding.carousel.currentPosition.takeIf { it != RecyclerView.NO_POSITION }?.let { currentPosition ->
                edit(currentPosition)
            }
        }

        binding.addPhotoButton.setOnClickListener {
            addPhoto()
        }

        binding.savePhotoButton.setOnClickListener {
            binding.carousel.currentPosition.takeIf { it != RecyclerView.NO_POSITION }?.let { currentPosition ->
                savePicture(it, currentPosition)
            }
        }

        binding.removePhotoButton.setOnClickListener {
            binding.carousel.currentPosition.takeIf { it != RecyclerView.NO_POSITION }?.let { currentPosition ->
                model.removeAt(currentPosition)
                model.cancelEncode(currentPosition)
            }
        }

        // Clean up temporary files, if any
        val tempFiles = requireActivity().intent.getStringArrayExtra(PostCreationActivity.TEMP_FILES)
        tempFiles?.asList()?.forEach {
            val file = File(binding.root.context.cacheDir, it)
            model.trackTempFile(file)
        }

        // Handle back pressed button
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val redraft = requireActivity().intent.getBooleanExtra(PostCreationActivity.POST_REDRAFT, false)
                if (redraft) {
                    MaterialAlertDialogBuilder(binding.root.context).apply {
                        setMessage(R.string.redraft_dialog_cancel)
                        setPositiveButton(android.R.string.ok) { _, _ ->
                            requireActivity().finish()
                        }
                        setNegativeButton(android.R.string.cancel) { _, _ -> }
                        show()
                    }
                } else {
                    requireActivity().finish()
                }
            }
        })
    }

    private val addPhotoResultContract = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data?.clipData != null) {
            result.data?.clipData?.let {
                model.setImages(model.addPossibleImages(it))
            }
        } else if (result.resultCode != Activity.RESULT_CANCELED) {
            Toast.makeText(requireActivity(), R.string.add_images_error, Toast.LENGTH_SHORT).show()
        }
    }

    private fun addPhoto(){
        addPhotoResultContract.launch(
            Intent(requireActivity(), CameraActivity::class.java)
        )
    }

    private fun savePicture(button: View, currentPosition: Int) {
        val originalUri = model.getPhotoData().value!![currentPosition].imageUri

        val pair = getOutputFile(originalUri)
        val outputStream: OutputStream = pair.first
        val path: String = pair.second

        requireActivity().contentResolver.openInputStream(originalUri)!!.use { input ->
            outputStream.use { output ->
                input.copyTo(output)
            }
        }

        if(path.startsWith("file")) {
            MediaScannerConnection.scanFile(
                requireActivity(),
                arrayOf(path.toUri().toFile().absolutePath),
                null
            ) { tried_path, uri ->
                if (uri == null) {
                    Log.e(
                        "NEW IMAGE SCAN FAILED",
                        "Tried to scan $tried_path, but it failed"
                    )
                }
            }
        }
        Snackbar.make(
            button, getString(R.string.save_image_success),
            Snackbar.LENGTH_LONG
        ).show()
    }

    private fun getOutputFile(uri: Uri): Pair<OutputStream, String> {
        val extension = uri.fileExtension(requireActivity().contentResolver)

        val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US)
            .format(System.currentTimeMillis()) + ".$extension"

        val outputStream: OutputStream
        val path: String
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver: ContentResolver = requireActivity().contentResolver
            val type = uri.getMimeType(requireActivity().contentResolver)
            val contentValues = ContentValues()
            contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            contentValues.put(MediaStore.MediaColumns.MIME_TYPE, type)
            contentValues.put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES
            )
            val store =
                if (type.startsWith("video")) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val imageUri: Uri = resolver.insert(store, contentValues)!!
            path = imageUri.toString()
            outputStream = resolver.openOutputStream(imageUri)!!
        } else {
            val imagesDir = Environment.getExternalStoragePublicDirectory(getString(R.string.app_name))
            imagesDir.mkdir()
            val file = File(imagesDir, name)
            path = Uri.fromFile(file).toString()
            outputStream = file.outputStream()
        }
        return Pair(outputStream, path)
    }


    private fun validatePost(): Boolean {
        if (model.getPhotoData().value?.all { !it.video || it.videoEncodeComplete } == false) {
            MaterialAlertDialogBuilder(requireActivity()).apply {
                setMessage(R.string.still_encoding)
                setNegativeButton(android.R.string.ok) { _, _ -> }
            }.show()
            return false
        }
        return true
    }

    private val editResultContract: ActivityResultLauncher<Intent> = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()){
            result: ActivityResult? ->
        if (result?.resultCode == Activity.RESULT_OK && result.data != null) {
            val position: Int = result.data!!.getIntExtra(PhotoEditActivity.PICTURE_POSITION, 0)
            model.modifyAt(position, result.data!!)
                ?: Toast.makeText(requireActivity(), R.string.error_editing, Toast.LENGTH_SHORT).show()
        } else if(result?.resultCode != Activity.RESULT_CANCELED){
            Toast.makeText(requireActivity(), R.string.error_editing, Toast.LENGTH_SHORT).show()
        }
    }

    private fun edit(position: Int) {
        val intent = Intent(
            requireActivity(),
            if (model.getPhotoData().value!![position].video) VideoEditActivity::class.java else PhotoEditActivity::class.java
        )
            .putExtra(PhotoEditActivity.PICTURE_URI, model.getPhotoData().value!![position].imageUri)
            .putExtra(PhotoEditActivity.PICTURE_POSITION, position)

        editResultContract.launch(intent)
    }
}

