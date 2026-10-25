package dev.yoanndev90.statusbarhider.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.core.profiles.Profile
import dev.yoanndev90.statusbarhider.core.profiles.ProfilesState
import dev.yoanndev90.statusbarhider.core.profiles.ProfilesStore
import dev.yoanndev90.statusbarhider.ui.components.SettingAction
import dev.yoanndev90.statusbarhider.ui.components.SettingGroup
import dev.yoanndev90.statusbarhider.ui.components.SettingHint
import dev.yoanndev90.statusbarhider.ui.components.SettingInfoRow
import dev.yoanndev90.statusbarhider.ui.components.SettingsScreen

/**
 * Profiles: named snapshots of the whole settings set. The active one is
 * shown with its match state; tapping a row switches to it (confirming first
 * when the live settings have unsaved modifications).
 */
@Composable
fun ProfilesScreen() {
	val vm: ProfilesViewModel = viewModel()
	val state by vm.profiles.collectAsStateWithLifecycle()
	val active by vm.active.collectAsStateWithLifecycle()
	val dirty by vm.dirty.collectAsStateWithLifecycle()
	var createOpen by rememberSaveable { mutableStateOf(false) }
	var renameTarget by rememberSaveable { mutableStateOf("") }
	var deleteTarget by rememberSaveable { mutableStateOf("") }
	var applyTarget by rememberSaveable { mutableStateOf("") }

	val current = active
	SettingsScreen {
		item {
			SettingGroup {
				SettingInfoRow(
					R.string.profile_active,
					current?.profile?.name ?: stringResource(R.string.profile_none)
				)
				SettingHint(
					text =
						when {
							current == null -> stringResource(R.string.hint_profile_none)
							dirty -> stringResource(R.string.hint_profile_modified)
							else -> stringResource(R.string.hint_profile_in_sync)
						},
					modifier = Modifier.padding(bottom = 4.dp)
				)
				if (current != null && dirty) {
					SettingAction(R.drawable.ic_save_alt, R.string.action_update_profile) {
						vm.updateProfile(current.profile.id)
					}
				}
			}
		}
		if (state.profiles.isNotEmpty()) {
			item {
				SettingGroup {
					state.profiles.forEach { profile ->
						ProfileRow(
							profile = profile,
							isActive = profile.id == state.activeId,
							onClick = {
								if (profile.id == state.activeId && !dirty) {
									// Already applied and in sync: nothing to do.
								} else if (dirty) {
									applyTarget = profile.id
								} else {
									vm.applyProfile(profile.id)
								}
							},
							onRename = { renameTarget = profile.id },
							onDelete = { deleteTarget = profile.id }
						)
					}
				}
			}
		} else {
			item {
				SettingGroup {
					SettingHint(R.string.profile_empty, Modifier.padding(vertical = 8.dp))
				}
			}
		}
		item {
			SettingGroup {
				SettingAction(R.drawable.ic_save_alt, R.string.action_save_profile) {
					createOpen = true
				}
			}
		}
	}

	ProfileDialogs(
		state = state,
		vm = vm,
		createOpen = createOpen,
		onCreateDismiss = { createOpen = false },
		renameTarget = renameTarget,
		onRenameDismiss = { renameTarget = "" },
		deleteTarget = deleteTarget,
		onDeleteDismiss = { deleteTarget = "" },
		applyTarget = applyTarget,
		onApplyDismiss = { applyTarget = "" }
	)
}

/**
 * Every profile dialog (create, rename, delete, apply-when-dirty), keyed off
 * the target ids so an unknown id simply renders nothing.
 */
@Composable
private fun ProfileDialogs(
	state: ProfilesState,
	vm: ProfilesViewModel,
	createOpen: Boolean,
	onCreateDismiss: () -> Unit,
	renameTarget: String,
	onRenameDismiss: () -> Unit,
	deleteTarget: String,
	onDeleteDismiss: () -> Unit,
	applyTarget: String,
	onApplyDismiss: () -> Unit
) {
	if (createOpen) {
		ProfileNameDialog(
			title = R.string.dialog_profile_new_title,
			initial = "",
			onDismiss = onCreateDismiss,
			onConfirm = { name ->
				if (vm.saveCurrent(name)) onCreateDismiss()
			}
		)
	}

	val renameProfile = state.profiles.find { it.id == renameTarget }
	if (renameProfile != null) {
		ProfileNameDialog(
			title = R.string.dialog_profile_rename_title,
			initial = renameProfile.name,
			onDismiss = onRenameDismiss,
			onConfirm = { name ->
				if (vm.renameProfile(renameProfile.id, name)) onRenameDismiss()
			}
		)
	}

	val deleteProfile = state.profiles.find { it.id == deleteTarget }
	if (deleteProfile != null) {
		ConfirmDialog(
			title = R.string.dialog_profile_delete_title,
			text = stringResource(R.string.dialog_profile_delete_text, deleteProfile.name),
			confirmLabel = R.string.action_delete_profile,
			onConfirm = {
				vm.deleteProfile(deleteProfile.id)
				onDeleteDismiss()
			},
			onDismiss = onDeleteDismiss
		)
	}

	val applyProfile = state.profiles.find { it.id == applyTarget }
	if (applyProfile != null) {
		ConfirmDialog(
			title = R.string.dialog_profile_apply_title,
			text = stringResource(R.string.dialog_profile_apply_text, applyProfile.name),
			confirmLabel = R.string.action_apply_profile,
			onConfirm = {
				vm.applyProfile(applyProfile.id)
				onApplyDismiss()
			},
			onDismiss = onApplyDismiss
		)
	}
}

/** Shared two-button confirmation; [text] is already resolved (it carries the profile name). */
@Composable
private fun ConfirmDialog(
	title: Int,
	text: String,
	confirmLabel: Int,
	onConfirm: () -> Unit,
	onDismiss: () -> Unit
) {
	AlertDialog(
		onDismissRequest = onDismiss,
		title = { Text(stringResource(title)) },
		text = { Text(text) },
		confirmButton = {
			TextButton(onClick = onConfirm) { Text(stringResource(confirmLabel)) }
		},
		dismissButton = {
			TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
		}
	)
}

/** One profile in the list: icon, name, active check and the rename / delete pair. */
@Composable
private fun ProfileRow(
	profile: Profile,
	isActive: Boolean,
	onClick: () -> Unit,
	onRename: () -> Unit,
	onDelete: () -> Unit
) {
	Row(
		modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		Icon(
			painter = painterResource(R.drawable.ic_person),
			contentDescription = null,
			tint = MaterialTheme.colorScheme.onSurfaceVariant,
			modifier = Modifier.padding(end = 12.dp)
		)
		Column(Modifier.weight(1f)) {
			Text(profile.name, style = MaterialTheme.typography.bodyLarge)
			Text(
				text = stringResource(if (isActive) R.string.profile_in_use else R.string.profile_saved),
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant
			)
		}
		if (isActive) {
			Icon(
				painter = painterResource(R.drawable.ic_check_circle),
				contentDescription = stringResource(R.string.cd_active),
				tint = MaterialTheme.colorScheme.primary
			)
			Spacer(Modifier.width(4.dp))
		}
		IconButton(onClick = onRename) {
			Icon(
				painter = painterResource(R.drawable.ic_edit),
				contentDescription = stringResource(R.string.cd_rename)
			)
		}
		IconButton(onClick = onDelete) {
			Icon(
				painter = painterResource(R.drawable.ic_delete),
				contentDescription = stringResource(R.string.cd_delete)
			)
		}
	}
}

/** New-name or rename dialog; a blank / oversized name keeps the dialog open. */
@Composable
private fun ProfileNameDialog(
	title: Int,
	initial: String,
	onDismiss: () -> Unit,
	onConfirm: (String) -> Unit
) {
	var name by rememberSaveable(initial) { mutableStateOf(initial) }
	val tooLong = name.trim().length > ProfilesStore.MAX_NAME_LENGTH
	AlertDialog(
		onDismissRequest = onDismiss,
		title = { Text(stringResource(title)) },
		text = {
			OutlinedTextField(
				value = name,
				onValueChange = { name = it },
				singleLine = true,
				isError = tooLong,
				supportingText = {
					if (tooLong) {
						Text(stringResource(R.string.err_profile_name_too_long, ProfilesStore.MAX_NAME_LENGTH))
					}
				},
				modifier = Modifier.fillMaxWidth()
			)
		},
		confirmButton = {
			TextButton(
				enabled = name.isNotBlank() && !tooLong,
				onClick = { onConfirm(name) }
			) { Text(stringResource(R.string.action_save)) }
		},
		dismissButton = {
			TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
		}
	)
}
