package tk.hacker1024.epimetheus.dialogs

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
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