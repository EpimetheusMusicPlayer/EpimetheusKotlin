package tk.hacker1024.epimetheus.dialogs

import android.content.Context
import android.content.DialogInterface
import android.view.View
import androidx.appcompat.app.AlertDialog
import com.google.android.material.snackbar.Snackbar
import tk.hacker1024.epimetheus.R

fun showLocationErrorDialog(
    context: Context,
    ok: (dialog: DialogInterface, which: Int) -> Unit = { dialog: DialogInterface, _: Int -> dialog.dismiss() },
    exit: (dialog: DialogInterface, which: Int) -> Unit,
    onClose: (dialog: DialogInterface) -> Unit = {}
) {
    AlertDialog.Builder(context)
        .setTitle(R.string.dialog_location_label)
        .setMessage(R.string.dialog_location_message)
        .setPositiveButton(R.string.dialog_default_ok, ok)
        .setNegativeButton(R.string.dialog_default_exit, exit)
        .setOnDismissListener(onClose)
        .setOnCancelListener(onClose)
        .show()!!
}

fun showNetworkErrorDialog(
    context: Context,
    ok: (dialog: DialogInterface, which: Int) -> Unit = { dialog: DialogInterface, _: Int -> dialog.dismiss() },
    exit: (dialog: DialogInterface, which: Int) -> Unit,
    onClose: (dialog: DialogInterface) -> Unit = {}
) {
    AlertDialog.Builder(context)
        .setTitle(R.string.dialog_network_label)
        .setMessage(R.string.dialog_network_message)
        .setPositiveButton(R.string.dialog_default_ok, ok)
        .setNegativeButton(R.string.dialog_default_exit, exit)
        .setOnDismissListener(onClose)
        .setOnCancelListener(onClose)
        .show()!!
}

fun showPandoraErrorDialog(
    message: String,
    context: Context,
    ok: (dialog: DialogInterface, which: Int) -> Unit = { dialog: DialogInterface, _: Int -> dialog.dismiss() },
    exit: (dialog: DialogInterface, which: Int) -> Unit,
    onClose: (dialog: DialogInterface) -> Unit = {}
) {
    AlertDialog.Builder(context)
        .setTitle(R.string.dialog_pandora_label)
        .setMessage(context.getString(R.string.dialog_pandora_message) + "\n\n$message")
        .setPositiveButton(R.string.dialog_default_ok, ok)
        .setNegativeButton(R.string.dialog_default_exit, exit)
        .setOnDismissListener(onClose)
        .setOnCancelListener(onClose)
        .show()!!
}

fun showAddStationConfirmationDialog(
    stationName: CharSequence,
    context: Context,
    ok: (dialog: DialogInterface, which: Int) -> Unit,
    cancel: (dialog: DialogInterface, which: Int) -> Unit = { dialog, _ -> dialog.dismiss()},
    onClose: (dialog: DialogInterface) -> Unit = { it.dismiss() }
) {
    AlertDialog.Builder(context)
        .setTitle(context.getString(R.string.dialog_add_confirm_label, stationName.trim()))
        .setMessage(R.string.dialog_add_confirm_message)
        .setPositiveButton(R.string.dialog_default_ok, ok)
        .setNegativeButton(android.R.string.cancel, cancel)
        .setOnDismissListener(onClose)
        .setOnCancelListener(onClose)
        .show()!!
}

fun showStationAddingSnackbar(
    stationName: CharSequence,
    view: View
) {
    Snackbar.make(
        view,
        view.context.getString(R.string.snackbar_adding_message, stationName),
        Snackbar.LENGTH_LONG
    ).show()
}

fun showStationAddedSnackbar(
    stationName: CharSequence,
    view: View,
    onAction: View.OnClickListener
) {
    Snackbar.make(
        view,
        view.context.getString(R.string.snackbar_added_message, stationName),
        Snackbar.LENGTH_LONG
    )
        .setAction(R.string.snackbar_added_action, onAction)
        .show()
}