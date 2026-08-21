@file:Suppress("DEPRECATION")
package com.example.ui

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.R
import com.example.viewmodel.SecurityViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class BlockedContactsActivity : BaseActivity() {

    private val viewModel: SecurityViewModel by viewModels()
    private lateinit var adapter: BlockedUsersAdapter

    private lateinit var etBlockUid: EditText
    private lateinit var btnBlockUser: Button
    private lateinit var rvBlockedUsers: RecyclerView
    private lateinit var progressBlocked: ProgressBar
    private lateinit var txtEmptyBlocked: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_blocked_contacts)

        // Back navigation
        findViewById<ImageView>(R.id.btn_back_blocked).setOnClickListener {
            triggerHapticFeedback()
            finish()
        }

        // Initialize Views
        etBlockUid = findViewById(R.id.et_block_uid)
        btnBlockUser = findViewById(R.id.btn_block_user)
        rvBlockedUsers = findViewById(R.id.rv_blocked_users)
        progressBlocked = findViewById(R.id.progress_blocked)
        txtEmptyBlocked = findViewById(R.id.txt_empty_blocked)

        // Setup RecyclerView
        rvBlockedUsers.layoutManager = LinearLayoutManager(this)
        adapter = BlockedUsersAdapter { targetUid ->
            triggerHapticFeedback()
            unblockUser(targetUid)
        }
        rvBlockedUsers.adapter = adapter

        // Setup Block Click Handler
        btnBlockUser.setOnClickListener {
            val targetUid = etBlockUid.text.toString().trim()
            if (targetUid.isEmpty()) {
                Toast.makeText(this, "Please enter a valid User ID", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            triggerHapticFeedback()
            blockUser(targetUid)
        }

        // Observe States
        observeViewModel()

        // Fetch Initial Blocked Users List
        viewModel.fetchBlockedUsers()
    }

    private fun blockUser(targetUid: String) {
        viewModel.blockUser(targetUid,
            onSuccess = {
                etBlockUid.text.clear()
                Toast.makeText(this, "User blocked successfully!", Toast.LENGTH_SHORT).show()
            },
            onFailure = { errorMsg ->
                Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show()
            }
        )
    }

    private fun unblockUser(targetUid: String) {
        viewModel.unblockUser(targetUid,
            onSuccess = {
                Toast.makeText(this, "User unblocked successfully!", Toast.LENGTH_SHORT).show()
            },
            onFailure = { errorMsg ->
                Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show()
            }
        )
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.isLoading.collectLatest { loading ->
                        progressBlocked.visibility = if (loading) View.VISIBLE else View.GONE
                    }
                }
                launch {
                    viewModel.blockedUsers.collectLatest { list ->
                        adapter.submitList(list)
                        if (list.isEmpty()) {
                            txtEmptyBlocked.visibility = View.VISIBLE
                            rvBlockedUsers.visibility = View.GONE
                        } else {
                            txtEmptyBlocked.visibility = View.GONE
                            rvBlockedUsers.visibility = View.VISIBLE
                        }
                    }
                }
            }
        }
    }

    private fun triggerHapticFeedback() {
        try {
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            vibrator?.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    it.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    it.vibrate(20)
                }
            }
        } catch (e: Exception) {
            window.decorView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }

    // RecyclerView Adapter Definition
    private class BlockedUsersAdapter(
        private val onUnblockClick: (String) -> Unit
    ) : RecyclerView.Adapter<BlockedUsersAdapter.ViewHolder>() {

        private var list: List<Pair<String, String>> = emptyList()

        fun submitList(newList: List<Pair<String, String>>) {
            list = newList
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_blocked_user, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val (uid, name) = list[position]
            holder.bind(uid, name, onUnblockClick)
        }

        override fun getItemCount(): Int = list.size

        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val txtName: TextView = itemView.findViewById(R.id.txt_blocked_name)
            private val txtUid: TextView = itemView.findViewById(R.id.txt_blocked_uid)
            private val btnUnblock: Button = itemView.findViewById(R.id.btn_unblock_user)

            fun bind(uid: String, name: String, onUnblockClick: (String) -> Unit) {
                txtName.text = name
                txtUid.text = "UID: $uid"
                btnUnblock.setOnClickListener {
                    onUnblockClick(uid)
                }
            }
        }
    }
}
