package com.example.kidsdrawingappudemy

import android.Manifest
import android.app.Activity.RESULT_OK
import android.app.Dialog
import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.media.Image
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.graphics.BitmapCompat
import androidx.core.view.get
import androidx.lifecycle.lifecycleScope
import com.example.kidsdrawingappudemy.databinding.FragmentDrawingBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class DrawingFragment : Fragment() {

    private val readExternalStorageResultLauncher: ActivityResultLauncher<String> =
        registerForActivityResult(ActivityResultContracts.RequestPermission()){
            isGranted ->
            if(isGranted) {
                //defining the intent of picking from Gallery the type of media is URI external
                //intents are used for starting a new activity which in the case is opening the APP Gallery
                val pickIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                openGalleryResultLauncher.launch(pickIntent)
            }
            else
                Toast.makeText(activity?.applicationContext, "Permission denied", Toast.LENGTH_SHORT).show()
        }

    private val writeExternalStorageResultLauncher: ActivityResultLauncher<String> =
        registerForActivityResult(ActivityResultContracts.RequestPermission()){
            isGranted ->
            if(isGranted){
                showProgressDialog()
                lifecycleScope.launch {
                    val bitmap = getBitmapFromView(binding.containerFrameLayout)
                    val result = saveBitmapFile(bitmap)
                    Toast.makeText(requireContext(), result, Toast.LENGTH_SHORT).show()
                }
            }else
                Toast.makeText(activity?.applicationContext, "Permission denied", Toast.LENGTH_SHORT).show()
        }

    //starting the activity described in the pickIntent, receiving and handling the result
    //URI defines the resource (our phone gallery path)
    private val openGalleryResultLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()){
            result ->
            if(result.resultCode == RESULT_OK && result.data != null){
                binding.backgroundImageView.setImageURI(result.data?.data)
            }
        }
    private lateinit var drawingView: DrawingView
    private var _binding: FragmentDrawingBinding? = null
    private lateinit var mImageButtonCurrentPaint: ImageButton
    private var customProgressDialog: Dialog? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentDrawingBinding.inflate(inflater, container, false)
        return binding.root

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setOnClickListeners()
        drawingView = binding.drawingView
        drawingView.setBrushSize(20f)
        val brushColors = binding.brushColorsButtons
        mImageButtonCurrentPaint = brushColors[2] as ImageButton //the default color is black
        mImageButtonCurrentPaint.setImageDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.pallet_selected))
        super.onViewCreated(view, savedInstanceState)
    }

    private fun setOnClickListeners() {
        binding.brushSizeButton.setOnClickListener {
            showBrushSizeDialog()
        }
        binding.skinColorButton.setOnClickListener {
            clickPaint(binding.skinColorButton)
        }
        binding.yellowColorButton.setOnClickListener {
            clickPaint(binding.yellowColorButton)
        }
        binding.blackColorButton.setOnClickListener {
            clickPaint(binding.blackColorButton)
        }
        binding.redColorButton.setOnClickListener {
            clickPaint(binding.redColorButton)
        }
        binding.greenColorButton.setOnClickListener {
            clickPaint(binding.greenColorButton)
        }
        binding.blueColorButton.setOnClickListener {
            clickPaint(binding.blueColorButton)
        }
        binding.whiteColorButton.setOnClickListener {
            clickPaint(binding.whiteColorButton)
        }
        binding.imagePickerButton.setOnClickListener {
            //checking if the current SDK version is >= than Android Marshmallow (23)
            //then we check if we need to use rationale to the user for requesting camera permission,
            //if returns true, then to the user was requested permission before and he denied it
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE)){
                showRationaleDialog("Permission demo request external storage access for reading", "External storage reading cannot be used because the user denied the permission.")
            }else{
                readExternalStorageResultLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
        binding.undoButton.setOnClickListener {
            drawingView.undoPath()
        }
        binding.redoButton.setOnClickListener {
            drawingView.redoPath()
        }
        binding.saveToGalleryButton.setOnClickListener {
            if(shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE))
                showRationaleDialog("Permission demo request external storage access for writing", "External storage writing cannot be used because the user denied the permission.")
            else
                writeExternalStorageResultLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun showBrushSizeDialog(){
        val brushDialog = Dialog(requireContext())
        brushDialog.setContentView(R.layout.dialog_brush_size)
        brushDialog.setTitle("Brush size: ")
        val smallBrush = brushDialog.findViewById<ImageButton>(R.id.smallBrush)
        smallBrush.setOnClickListener{
            drawingView.setBrushSize(10f)
            brushDialog.dismiss()
        }
        val mediumBrush = brushDialog.findViewById<ImageButton>(R.id.mediumBrush)
        mediumBrush.setOnClickListener{
            drawingView.setBrushSize(20f)
            brushDialog.dismiss()
        }
        val bigBrush = brushDialog.findViewById<ImageButton>(R.id.bigBrush)
        bigBrush.setOnClickListener{
            drawingView.setBrushSize(30f)
            brushDialog.dismiss()
        }
        brushDialog.show()
    }

    fun clickPaint(view: View){
        if(view !== mImageButtonCurrentPaint){
            val imageButton = view as ImageButton
            val colorTag = imageButton.tag.toString()
            drawingView.setColor(colorTag)
            //old unselected color back to normal
            mImageButtonCurrentPaint.setImageDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.pallet_normal))

            //setting up the selected color
            mImageButtonCurrentPaint = imageButton
            mImageButtonCurrentPaint.setImageDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.pallet_selected))
        }
    }

    private fun showRationaleDialog(title: String, message: String){
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle(title)
            .setMessage(message)
            .setPositiveButton("Cancel"){dialog, _->dialog.dismiss()}
        builder.create().show()
    }

    private fun getBitmapFromView(view: View) : Bitmap {
        //first create an empty bitmap with width and length of the view
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        //then make a canvas out of the empty bitmap
        val canvas = Canvas(bitmap)
        //get the background if there is any
        val backgroundDrawable = view.background
        if(backgroundDrawable != null)
            backgroundDrawable.draw(canvas) //in case there is background, draw it on the canvas
        else
            canvas.drawColor(Color.WHITE) //in case there is NO background, just draw white on the canvas as background

        view.draw(canvas) //then draw the contents of the view (brush color paths) on the canvas

        return bitmap //return the bitmap made out of the canvas content
    }

    private suspend fun saveBitmapFile(bitmap: Bitmap?) : String{ //suspend means a coroutine function that is on other thread different from the main one
        var result = ""
        withContext(Dispatchers.IO){ //working on the Coroutine Input Output
            if(bitmap != null){
                try{
                    val bytes = ByteArrayOutputStream() //we need to transform the bitmap into byte output stream (bytes) in order to write the bytes into a file
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, bytes) //compress and write it into the stream
                    val file = File(context?.externalCacheDir?.absoluteFile.toString() + File.separator + "kids_drawing_app" + System.currentTimeMillis()/1000+".jpg")
                    val fileOutputStream = FileOutputStream(file) //creating file output stream to write the bitmap there
                    fileOutputStream.write(bytes.toByteArray())
                    fileOutputStream.close()

                    result = file.absolutePath

                    requireActivity().runOnUiThread{ //stuff that we need to display on screen, so the UI thread is needed
                        cancelProgressDialog()
                        if(result.isNotEmpty()){
                            Toast.makeText(requireContext(), "File saved successfully", Toast.LENGTH_SHORT).show()
                            shareFile(result)
                        }else
                            Toast.makeText(requireContext(), "File not saved", Toast.LENGTH_SHORT).show()
                    }
                }catch (e: java.lang.Exception){
                    result = ""
                    e.printStackTrace()
                }
            }
        }
        return result
    }

    private fun showProgressDialog(){
        customProgressDialog = Dialog(requireContext())
        customProgressDialog!!.setContentView(R.layout.dialog_custom_progress)
        customProgressDialog!!.show()
    }

    private fun cancelProgressDialog(){
        if(customProgressDialog != null) {
            customProgressDialog!!.dismiss()
            customProgressDialog = null
        }
    }

    private fun shareFile(absolutePath: String) {
        try {
            val imageFile = File(absolutePath) //we create a File from the absolute path
            val imageUri = FileProvider.getUriForFile(
                requireContext(),
                requireContext().applicationContext.packageName + ".provider",
                imageFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND)
            shareIntent.type = "image/*"
            shareIntent.putExtra(Intent.EXTRA_STREAM, imageUri)
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

            startActivity(Intent.createChooser(shareIntent, "Share"))
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(requireContext(), "Error sharing image", Toast.LENGTH_SHORT).show()
        }
    }
}