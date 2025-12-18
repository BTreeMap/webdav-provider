package org.joefang.webdav.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.core.widget.doAfterTextChanged
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import dagger.hilt.android.AndroidEntryPoint
import org.joefang.webdav.R
import org.joefang.webdav.adapters.CustomHeaderAdapter
import org.joefang.webdav.data.CustomHeader
import org.joefang.webdav.databinding.FragmentCustomHeadersBinding

@AndroidEntryPoint
class CustomHeadersFragment : Fragment(), CustomHeaderAdapter.Listener {

    private val args: CustomHeadersFragmentArgs by navArgs()
    private lateinit var binding: FragmentCustomHeadersBinding
    private lateinit var adapter: CustomHeaderAdapter

    private var hasChanges = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_custom_headers, container, false)
        
        adapter = CustomHeaderAdapter(this)
        binding.rvCustomHeaders.layoutManager = LinearLayoutManager(context)
        binding.rvCustomHeaders.adapter = adapter

        // Load existing headers from args
        val existingHeaders = args.headers?.toList() ?: emptyList()
        adapter.setHeaders(existingHeaders)
        updateEmptyState()

        binding.fabAddHeader.setOnClickListener {
            showHeaderDialog(null, -1)
        }

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, BackPressedCallback())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menu.clear()
                menuInflater.inflate(R.menu.menu_account, menu)
                menu.findItem(R.id.action_delete)?.isVisible = false
            }

            override fun onMenuItemSelected(item: MenuItem): Boolean {
                return handleMenuItem(item)
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun handleMenuItem(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_save -> {
                saveAndReturn()
                true
            }
            android.R.id.home -> {
                tryClose()
                true
            }
            else -> false
        }
    }

    private fun saveAndReturn() {
        val result = Bundle().apply {
            putParcelableArray(RESULT_HEADERS, adapter.getHeaders().toTypedArray())
        }
        parentFragmentManager.setFragmentResult(REQUEST_KEY, result)
        findNavController().popBackStack()
    }

    private fun showHeaderDialog(header: CustomHeader?, position: Int) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_custom_header, null)
        val textLayoutName = dialogView.findViewById<TextInputLayout>(R.id.text_layout_header_name)
        val textName = dialogView.findViewById<TextInputEditText>(R.id.text_header_name)
        val textLayoutValue = dialogView.findViewById<TextInputLayout>(R.id.text_layout_header_value)
        val textValue = dialogView.findViewById<TextInputEditText>(R.id.text_header_value)
        val checkboxSecret = dialogView.findViewById<MaterialCheckBox>(R.id.checkbox_treat_as_secret)
        val textWarning = dialogView.findViewById<TextView>(R.id.text_warning)

        // Pre-populate if editing
        if (header != null) {
            textName.setText(header.name)
            textValue.setText(header.value)
            checkboxSecret.isChecked = header.isSecret

            // Update password toggle based on secret checkbox
            updateValueInputType(textLayoutValue, textValue, header.isSecret)
        }

        // Show warning for dangerous headers
        textName.doAfterTextChanged { text ->
            val name = text?.toString() ?: ""
            textWarning.visibility = if (CustomHeader.isDangerousHeader(name)) {
                View.VISIBLE
            } else {
                View.GONE
            }
        }

        // Toggle password mode based on secret checkbox
        checkboxSecret.setOnCheckedChangeListener { _, isChecked ->
            updateValueInputType(textLayoutValue, textValue, isChecked)
        }

        val title = if (header == null) R.string.custom_header_add else R.string.action_edit
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setView(dialogView)
            .setPositiveButton(R.string.action_save) { _, _ ->
                val name = textName.text?.toString()?.trim() ?: ""
                val value = textValue.text?.toString() ?: ""
                val isSecret = checkboxSecret.isChecked

                // Validate
                val nameError = CustomHeader.validateHeaderName(name)
                if (nameError != null) {
                    textLayoutName.error = nameError
                    return@setPositiveButton
                }

                val newHeader = CustomHeader(name, value, isSecret, header?.enabled ?: true)
                if (position >= 0) {
                    adapter.updateHeader(position, newHeader)
                } else {
                    adapter.addHeader(newHeader)
                }
                hasChanges = true
                updateEmptyState()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun updateValueInputType(layout: TextInputLayout, editText: TextInputEditText, isSecret: Boolean) {
        if (isSecret) {
            layout.endIconMode = com.google.android.material.textfield.TextInputLayout.END_ICON_PASSWORD_TOGGLE
            editText.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        } else {
            layout.endIconMode = com.google.android.material.textfield.TextInputLayout.END_ICON_NONE
            editText.inputType = android.text.InputType.TYPE_CLASS_TEXT
        }
    }

    private fun updateEmptyState() {
        binding.isEmpty = adapter.itemCount == 0
    }

    override fun onEditHeader(position: Int, header: CustomHeader) {
        showHeaderDialog(header, position)
    }

    override fun onDeleteHeader(position: Int, header: CustomHeader) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.action_delete)
            .setMessage(getString(R.string.custom_header_name) + ": " + header.name)
            .setPositiveButton(R.string.yes) { _, _ ->
                adapter.removeHeader(position)
                hasChanges = true
                updateEmptyState()
            }
            .setNegativeButton(R.string.no, null)
            .show()
    }

    override fun onHeaderEnabledChanged(position: Int, header: CustomHeader) {
        hasChanges = true
    }

    private fun tryClose(): Boolean {
        if (hasChanges) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.dialog_title_discard_changes)
                .setMessage(R.string.dialog_message_discard_changes)
                .setPositiveButton(R.string.yes) { _, _ ->
                    findNavController().popBackStack()
                }
                .setNegativeButton(R.string.no, null)
                .show()
            return false
        }

        findNavController().popBackStack()
        return true
    }

    private inner class BackPressedCallback : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (tryClose() && isEnabled) {
                isEnabled = false
            }
        }
    }

    companion object {
        const val REQUEST_KEY = "custom_headers_request"
        const val RESULT_HEADERS = "result_headers"
    }
}
