package dev.rocli.android.webdav.fragments

import android.os.Bundle
import android.security.KeyChain
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.databinding.BindingAdapter
import androidx.databinding.DataBindingUtil
import androidx.databinding.InverseBindingAdapter
import androidx.databinding.InverseBindingListener
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import dagger.hilt.android.AndroidEntryPoint
import dev.rocli.android.webdav.R
import dev.rocli.android.webdav.data.Account
import dev.rocli.android.webdav.data.AccountDao
import dev.rocli.android.webdav.data.CustomHeader
import dev.rocli.android.webdav.data.HeaderProfile
import dev.rocli.android.webdav.data.SecretString
import dev.rocli.android.webdav.databinding.FragmentAccountBinding
import dev.rocli.android.webdav.dialogs.Dialogs
import dev.rocli.android.webdav.provider.WebDavCache
import dev.rocli.android.webdav.provider.WebDavClientManager
import dev.rocli.android.webdav.provider.WebDavPath
import dev.rocli.android.webdav.provider.WebDavProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrl
import javax.inject.Inject

@AndroidEntryPoint
class AccountFragment : Fragment() {
    @Inject
    lateinit var accountDao: AccountDao

    @Inject
    lateinit var webDavCache: WebDavCache

    @Inject
    lateinit var clients: WebDavClientManager

    private var optionsMenu: Menu? = null

    private val args: AccountFragmentArgs by navArgs()
    private lateinit var binding: FragmentAccountBinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_account, container, false)
        if (args.id != -1L) {
            binding.account = accountDao.getById(args.id)!!.copy()
        } else {
            binding.account = Account()
        }

        val protocolAdapter = ArrayAdapter.createFromResource(requireContext(), R.array.protocol_options, R.layout.dropdown_list_item)
        binding.dropdownProtocol.setAdapter(protocolAdapter)

        val authTypeAdapter = ArrayAdapter.createFromResource(requireContext(), R.array.auth_type_options, R.layout.dropdown_list_item)
        binding.dropdownAuthType.setAdapter(authTypeAdapter)
        binding.dropdownAuthType.doAfterTextChanged {
            binding.account?.let {
                if (it.authType == Account.AuthType.NONE) {
                    it.username = null
                    it.password = null
                    binding.invalidateAll()
                }
            }
            updateUserPassVisibility()
        }
        updateUserPassVisibility()

        if (binding.account!!.clientCert.isNullOrBlank()) {
            binding.textLayoutCertificate.setEndIconDrawable(R.drawable.ic_outline_add_24)
        } else {
            binding.textLayoutCertificate.setEndIconDrawable(R.drawable.ic_outline_delete_24)
        }
        binding.textLayoutCertificate.setEndIconOnClickListener {
            if (binding.textCertificate.text.toString().isBlank()) {
                if (validateForm(true)) {
                    val url = binding.textUrl.text.toString().toHttpUrl()
                    KeyChain.choosePrivateKeyAlias(
                        requireActivity(), { alias ->
                            requireActivity().runOnUiThread {
                                if (alias != null) {
                                    binding.textLayoutCertificate.setEndIconDrawable(R.drawable.ic_outline_delete_24)
                                    binding.textCertificate.setText(alias)
                                } else {
                                    // TODO: there is probably a better way to only show the toast if no certificate(s) are installed
                                    Toast.makeText(
                                        requireActivity(),
                                        getString(R.string.notice_no_client_certificate),
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        },
                        null,
                        null,
                        url.host,
                        url.port,
                        url.host
                    )
                }
            } else {
                binding.textLayoutCertificate.setEndIconDrawable(R.drawable.ic_outline_add_24)
                binding.textLayoutCertificate.editText?.text?.clear()
            }
        }

        binding.sliderMaxCacheFileSize.apply {
            setLabelFormatter { getString(R.string.value_max_cache_file_size, it.toInt()) }
            addOnChangeListener(Slider.OnChangeListener { _, value, _ ->
                // TODO: figure out why this update doesn't automatically happen with data binding
                binding.valueCacheFileSize.text = getString(R.string.value_max_cache_file_size, value.toLong())
            })
        }

        // Header profile dropdown setup
        val headerProfileAdapter = ArrayAdapter.createFromResource(requireContext(), R.array.header_profile_options, R.layout.dropdown_list_item)
        binding.dropdownHeaderProfile.setAdapter(headerProfileAdapter)
        binding.dropdownHeaderProfile.doAfterTextChanged {
            binding.account?.let {
                when (it.headerProfile) {
                    HeaderProfile.NONE -> {
                        it.cfAccessClientId = null
                        it.cfAccessClientSecret = null
                        it.customHeaders = null
                        binding.invalidateAll()
                    }
                    HeaderProfile.CLOUDFLARE -> {
                        it.customHeaders = null
                    }
                    HeaderProfile.CUSTOM -> {
                        it.cfAccessClientId = null
                        it.cfAccessClientSecret = null
                    }
                }
            }
            updateHeaderProfileVisibility()
        }
        updateHeaderProfileVisibility()

        // Custom headers button click
        binding.btnEditCustomHeaders.setOnClickListener {
            val headers = binding.account?.customHeaders?.toTypedArray()
            val action = AccountFragmentDirections.actionAccountFragmentToCustomHeadersFragment(headers)
            findNavController().navigate(action)
        }

        // Listen for results from CustomHeadersFragment
        parentFragmentManager.setFragmentResultListener(CustomHeadersFragment.REQUEST_KEY, viewLifecycleOwner) { _, bundle ->
            @Suppress("DEPRECATION")
            val headers = bundle.getParcelableArray(CustomHeadersFragment.RESULT_HEADERS)
            binding.account?.customHeaders = headers?.map { it as CustomHeader }
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
                if (args.id == -1L) {
                    menu.findItem(R.id.action_delete).isVisible = false
                }
                optionsMenu = menu
            }

            override fun onMenuItemSelected(item: MenuItem): Boolean {
                return handleMenuItem(item)
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun handleMenuItem(item: MenuItem): Boolean {
        val account = binding.account!!

        return when (item.itemId) {
            R.id.action_save -> {
                if (validateForm(binding.textCertificate.text.toString().isNotBlank())) {
                    updateTestStatus(true)
                    val job = lifecycleScope.launch(Dispatchers.IO) {
                        clients.delete(account)
                        val res = clients.get(account).propFind(WebDavPath(account.rootPath, true))
                        if (res.isSuccessful) {
                            webDavCache.clearFileMeta(account)
                            webDavCache.setFileMeta(account, res.body!!)
                            if (account.id == 0L) {
                                accountDao.insert(account)
                            } else {
                                accountDao.update(account)
                            }

                            WebDavProvider.notifyChangeRoots(requireContext())
                            lifecycleScope.launch(Dispatchers.Main) { close() }
                        } else {
                            lifecycleScope.launch(Dispatchers.Main) {
                                Snackbar
                                    .make(
                                        requireView(),
                                        getString(R.string.error_webdav_connection, res.error?.message),
                                        BaseTransientBottomBar.LENGTH_LONG
                                    )
                                    .setAction(R.string.action_details) {
                                        Dialogs.showErrorDialog(requireContext(), R.string.error_webdav_connection_dialog, res.error!!)
                                    }
                                    .show()
                            }
                        }
                    }
                    job.invokeOnCompletion {
                        if (it == null) {
                            lifecycleScope.launch(Dispatchers.Main) {
                                updateTestStatus(false)
                            }
                        }
                    }
                }
                true
            }
            R.id.action_delete -> {
                Dialogs.showRemoveAccountsDialog(requireContext(), listOf(account)) { _, _ ->
                    accountDao.delete(account)

                    WebDavProvider.notifyChangeRoots(requireContext())
                    close()
                }
                true
            }
            android.R.id.home -> {
                tryClose()
                true
            }
            else -> {
                false
            }
        }
    }

    private fun updateUserPassVisibility() {
        val visibility = if (binding.account!!.authType == Account.AuthType.NONE)
            View.GONE else View.VISIBLE
        binding.textLayoutUsername.visibility = visibility
        binding.textLayoutPassword.visibility = visibility
    }

    private fun updateHeaderProfileVisibility() {
        val account = binding.account ?: return
        
        // Cloudflare fields are visible only when Cloudflare profile is selected
        val cloudflareVisible = account.headerProfile == HeaderProfile.CLOUDFLARE
        binding.textLayoutCfClientId.visibility = if (cloudflareVisible) View.VISIBLE else View.GONE
        binding.textLayoutCfClientSecret.visibility = if (cloudflareVisible) View.VISIBLE else View.GONE
        binding.tvCfHelperText.visibility = if (cloudflareVisible) View.VISIBLE else View.GONE
        
        // Custom headers button is visible only when Custom profile is selected
        val customVisible = account.headerProfile == HeaderProfile.CUSTOM
        binding.btnEditCustomHeaders.visibility = if (customVisible) View.VISIBLE else View.GONE

        val anchorId = when {
            cloudflareVisible -> binding.tvCfHelperText.id
            customVisible -> binding.btnEditCustomHeaders.id
            else -> binding.layoutHeaderProfile.id
        }
        updateAdvancedSectionAnchor(anchorId)
    }

    private fun updateAdvancedSectionAnchor(anchorId: Int) {
        val topMargin = (binding.tvAdvanced.layoutParams as ConstraintLayout.LayoutParams).topMargin
        ConstraintSet().apply {
            clone(binding.layoutForm)
            clear(binding.tvAdvanced.id, ConstraintSet.TOP)
            connect(binding.tvAdvanced.id, ConstraintSet.TOP, anchorId, ConstraintSet.BOTTOM, topMargin)
            applyTo(binding.layoutForm)
        }
    }

    private fun validateForm(clientCert: Boolean = false): Boolean {
        var res = true
        if (binding.textName.text.toString().isBlank()) {
            getInputLayout(binding.textName).error = getString(R.string.error_field_required)
            res = false
        } else {
            getInputLayout(binding.textName).let {
                it.error = null
                it.isErrorEnabled = false
            }
        }

        if (binding.textLayoutUsername.isVisible && binding.textUsername.text.toString().isBlank()) {
            getInputLayout(binding.textUsername).error = getString(R.string.error_field_required)
            res = false
        } else {
            getInputLayout(binding.textUsername).let {
                it.error = null
                it.isErrorEnabled = false
            }
        }

        if (binding.textLayoutPassword.isVisible && binding.textPassword.text.toString().isBlank()) {
            getInputLayout(binding.textPassword).error = getString(R.string.error_field_required)
            res = false
        } else {
            getInputLayout(binding.textPassword).let {
                it.error = null
                it.isErrorEnabled = false
            }
        }

        try {
            val url = binding.textUrl.text.toString().toHttpUrl()
            if (clientCert && !url.isHttps) {
                getInputLayout(binding.textUrl).error = getString(R.string.notice_http_client_certificate)
                res = false
            } else {
                getInputLayout(binding.textUrl).let {
                    it.error = null
                    it.isErrorEnabled = false
                }
            }
        } catch (e: IllegalArgumentException) {
            getInputLayout(binding.textUrl).error = getString(R.string.error_invalid_url)
            res = false
        }

        // Validate Cloudflare fields when Cloudflare profile is selected
        if (binding.account?.headerProfile == HeaderProfile.CLOUDFLARE) {
            if (binding.textLayoutCfClientId.isVisible && binding.textCfClientId.text.toString().isBlank()) {
                binding.textLayoutCfClientId.error = getString(R.string.error_field_required)
                res = false
            } else {
                binding.textLayoutCfClientId.error = null
                binding.textLayoutCfClientId.isErrorEnabled = false
            }

            if (binding.textLayoutCfClientSecret.isVisible && binding.textCfClientSecret.text.toString().isBlank()) {
                binding.textLayoutCfClientSecret.error = getString(R.string.error_field_required)
                res = false
            } else {
                binding.textLayoutCfClientSecret.error = null
                binding.textLayoutCfClientSecret.isErrorEnabled = false
            }
        }

        return res
    }

    private fun getInputLayout(text: TextInputEditText): TextInputLayout {
        return text.parent.parent as TextInputLayout
    }

    private fun tryClose(): Boolean {
        val origAccount = if (args.id == -1L) Account() else accountDao.getById(args.id)
        val formAccount = binding.account!!.copy(id = origAccount!!.id)

        if (origAccount != formAccount) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.dialog_title_discard_changes)
                .setMessage(R.string.dialog_message_discard_changes)
                .setPositiveButton(R.string.yes) { _, _ ->
                    close()
                }
                .setNegativeButton(R.string.no, null)
                .show()
            return false
        }

        close()
        return true
    }

    private fun close() {
        view?.clearFocus()
        findNavController().popBackStack()
    }

    private fun updateTestStatus(testing: Boolean) {
        setTitle(if (testing) getString(R.string.webdav_testing_connection) else args.title)
        optionsMenu?.setGroupEnabled(R.id.menu_action_group, !testing)
        setIsLayoutEnabled(binding.layoutForm, !testing)
        binding.progressIndicator.visibility = if (testing) View.VISIBLE else View.INVISIBLE
    }

    private fun setTitle(title: String) {
        (requireActivity() as AppCompatActivity).supportActionBar!!.title = title
    }

    private fun setIsLayoutEnabled(group: ViewGroup, enabled: Boolean) {
        for (child in group.children) {
            child.isEnabled = enabled
            if (child is ViewGroup) {
                setIsLayoutEnabled(child, enabled)
            }
        }
    }

    private inner class BackPressedCallback : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (tryClose() && isEnabled) {
                isEnabled = false
            }
        }
    }
}

@BindingAdapter("android:valueAttrChanged")
fun Slider.setSliderListeners(attrChange: InverseBindingListener) {
    this.addOnChangeListener { _, _, _ ->
        attrChange.onChange()
    }
}

@BindingAdapter("android:value")
fun Slider.setSliderValueLong(newValue: Long) {
    val fNewValue = newValue.toFloat()
    if (this.value != fNewValue) {
        this.value = fNewValue
    }
}

@InverseBindingAdapter(attribute = "android:value")
fun Slider.getSliderValueLong(): Long {
    return this.value.toLong()
}

@BindingAdapter("android:text")
fun AutoCompleteTextView.setDropdownValueProtocol(newValue: Account.Protocol) {
    val array = this.resources!!.getStringArray(R.array.protocol_options)
    val text = array[newValue.ordinal]
    if (this.text.toString() != text) {
        this.setText(text, false)
    }
}

@InverseBindingAdapter(attribute = "android:text")
fun AutoCompleteTextView.getDropdownValueProtocol(): Account.Protocol {
    val array = this.resources!!.getStringArray(R.array.protocol_options)
    return Account.Protocol.entries[array.indexOf(this.text.toString())]
}

@BindingAdapter("android:text")
fun AutoCompleteTextView.setDropdownValueAuthType(newValue: Account.AuthType) {
    val array = this.resources!!.getStringArray(R.array.auth_type_options)
    val text = array[newValue.ordinal]
    if (this.text.toString() != text) {
        this.setText(text, false)
    }
}

@InverseBindingAdapter(attribute = "android:text")
fun AutoCompleteTextView.getDropdownValueAuthType(): Account.AuthType {
    val array = this.resources!!.getStringArray(R.array.auth_type_options)
    return Account.AuthType.entries[array.indexOf(this.text.toString())]
}

@BindingAdapter("android:text")
fun AutoCompleteTextView.setDropdownValueHeaderProfile(newValue: HeaderProfile) {
    val array = this.resources!!.getStringArray(R.array.header_profile_options)
    val text = array[newValue.ordinal]
    if (this.text.toString() != text) {
        this.setText(text, false)
    }
}

@InverseBindingAdapter(attribute = "android:text")
fun AutoCompleteTextView.getDropdownValueHeaderProfile(): HeaderProfile {
    val array = this.resources!!.getStringArray(R.array.header_profile_options)
    val index = array.indexOf(this.text.toString())
    return if (index >= 0 && index < HeaderProfile.entries.size) {
        HeaderProfile.entries[index]
    } else {
        HeaderProfile.NONE
    }
}

@BindingAdapter("android:text")
fun TextInputEditText.setSecretStringValue(newValue: SecretString?) {
    this.setText(newValue?.value)
}

@InverseBindingAdapter(attribute = "android:text")
fun TextInputEditText.getSecretStringValue(): SecretString? {
    if (this.text.isNullOrBlank()) {
        return null
    }

    return SecretString(this.text.toString())
}
