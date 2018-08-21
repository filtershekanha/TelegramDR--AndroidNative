/*
 * This is the source code of Telegram for Android v. 3.x.x
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package com.filtershekanha.teledr.ui;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.os.Vibrator;
import android.text.InputType;
import android.text.TextUtils;
import android.text.method.PasswordTransformationMethod;
import android.util.TypedValue;
import android.view.ActionMode;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.filtershekanha.teledr.messenger.AndroidUtilities;
import com.filtershekanha.teledr.messenger.FileLog;
import com.filtershekanha.teledr.messenger.LocaleController;
import com.filtershekanha.teledr.messenger.NotificationCenter;
import com.filtershekanha.teledr.messenger.R;
import com.filtershekanha.teledr.messenger.UserConfig;
import com.filtershekanha.teledr.messenger.Utilities;
import com.filtershekanha.teledr.messenger.browser.Browser;
import com.filtershekanha.teledr.messenger.support.widget.LinearLayoutManager;
import com.filtershekanha.teledr.messenger.support.widget.RecyclerView;
import com.filtershekanha.teledr.tgnet.ConnectionsManager;
import com.filtershekanha.teledr.tgnet.RequestDelegate;
import com.filtershekanha.teledr.tgnet.TLObject;
import com.filtershekanha.teledr.tgnet.TLRPC;
import com.filtershekanha.teledr.ui.ActionBar.ActionBar;
import com.filtershekanha.teledr.ui.ActionBar.ActionBarMenu;
import com.filtershekanha.teledr.ui.ActionBar.ActionBarMenuItem;
import com.filtershekanha.teledr.ui.ActionBar.AlertDialog;
import com.filtershekanha.teledr.ui.ActionBar.BaseFragment;
import com.filtershekanha.teledr.ui.ActionBar.Theme;
import com.filtershekanha.teledr.ui.ActionBar.ThemeDescription;
import com.filtershekanha.teledr.ui.Cells.TextInfoPrivacyCell;
import com.filtershekanha.teledr.ui.Cells.TextSettingsCell;
import com.filtershekanha.teledr.ui.Components.EditTextBoldCursor;
import com.filtershekanha.teledr.ui.Components.EmptyTextProgressView;
import com.filtershekanha.teledr.ui.Components.LayoutHelper;
import com.filtershekanha.teledr.ui.Components.RecyclerListView;

public class TwoStepVerificationActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private ListAdapter listAdapter;
    private RecyclerListView listView;
    private TextView titleTextView;
    private TextView bottomTextView;
    private TextView bottomButton;
    private EditTextBoldCursor passwordEditText;
    private AlertDialog progressDialog;
    private EmptyTextProgressView emptyView;
    private ActionBarMenuItem doneItem;
    private ScrollView scrollView;

    private int type;
    private int passwordSetState;
    private String firstPassword;
    private String hint;
    private String email;
    private boolean emailOnly;
    private boolean loading;
    private boolean destroyed;
    private boolean paused;
    private boolean waitingForEmail;
    private TLRPC.account_Password currentPassword;
    private boolean passwordEntered = true;
    private byte[] currentPasswordHash = new byte[0];
    private byte[] currentSecretSalt;
    private long currentSecretId;
    private byte[] currentSecret;
    private Runnable shortPollRunnable;
    private boolean closeAfterSet;

    private int setPasswordRow;
    private int setPasswordDetailRow;
    private int changePasswordRow;
    private int shadowRow;
    private int turnPasswordOffRow;
    private int setRecoveryEmailRow;
    private int changeRecoveryEmailRow;
    private int abortPasswordRow;
    private int passwordSetupDetailRow;
    private int passwordEnabledDetailRow;
    private int passwordEmailVerifyDetailRow;
    private int rowCount;

    private final static int done_button = 1;

    public TwoStepVerificationActivity(int type) {
        super();
        this.type = type;
        if (type == 0) {
            loadPasswordInfo(false);
        }
    }

    public TwoStepVerificationActivity(int account, int type) {
        super();
        currentAccount = account;
        this.type = type;
        if (type == 0) {
            loadPasswordInfo(false);
        }
    }

    protected void setRecoveryParams(TLRPC.account_Password password) {
        currentPassword = password;
        passwordSetState = 4;
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        updateRows();
        if (type == 0) {
            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.didSetTwoStepPassword);
        }
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        if (type == 0) {
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.didSetTwoStepPassword);
            if (shortPollRunnable != null) {
                AndroidUtilities.cancelRunOnUIThread(shortPollRunnable);
                shortPollRunnable = null;
            }
            destroyed = true;
        }
        if (progressDialog != null) {
            try {
                progressDialog.dismiss();
            } catch (Exception e) {
                FileLog.e(e);
            }
            progressDialog = null;
        }
        AndroidUtilities.removeAdjustResize(getParentActivity(), classGuid);
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(false);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == done_button) {
                    processDone();
                }
            }
        });

        fragmentView = new FrameLayout(context);
        FrameLayout frameLayout = (FrameLayout) fragmentView;
        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));

        ActionBarMenu menu = actionBar.createMenu();
        doneItem = menu.addItemWithWidth(done_button, R.drawable.ic_done, AndroidUtilities.dp(56));

        scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);
        frameLayout.addView(scrollView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(linearLayout, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP));

        titleTextView = new TextView(context);
        titleTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText6));
        titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        titleTextView.setGravity(Gravity.CENTER_HORIZONTAL);
        linearLayout.addView(titleTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 38, 0, 0));

        passwordEditText = new EditTextBoldCursor(context);
        passwordEditText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        passwordEditText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        passwordEditText.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
        passwordEditText.setBackgroundDrawable(Theme.createEditTextDrawable(context, false));
        passwordEditText.setMaxLines(1);
        passwordEditText.setLines(1);
        passwordEditText.setGravity(Gravity.CENTER_HORIZONTAL);
        passwordEditText.setSingleLine(true);
        passwordEditText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        passwordEditText.setTransformationMethod(PasswordTransformationMethod.getInstance());
        passwordEditText.setTypeface(Typeface.DEFAULT);
        passwordEditText.setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        passwordEditText.setCursorSize(AndroidUtilities.dp(20));
        passwordEditText.setCursorWidth(1.5f);
        linearLayout.addView(passwordEditText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 36, Gravity.TOP | Gravity.LEFT, 40, 32, 40, 0));
        passwordEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                if (i == EditorInfo.IME_ACTION_NEXT || i == EditorInfo.IME_ACTION_DONE) {
                    processDone();
                    return true;
                }
                return false;
            }
        });
        passwordEditText.setCustomSelectionActionModeCallback(new ActionMode.Callback() {
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                return false;
            }

            public void onDestroyActionMode(ActionMode mode) {
            }

            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                return false;
            }

            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                return false;
            }
        });

        bottomTextView = new TextView(context);
        bottomTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText6));
        bottomTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        bottomTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
        bottomTextView.setText(LocaleController.getString("YourEmailInfo", R.string.YourEmailInfo));
        linearLayout.addView(bottomTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 40, 30, 40, 0));

        LinearLayout linearLayout2 = new LinearLayout(context);
        linearLayout2.setGravity(Gravity.BOTTOM | Gravity.CENTER_VERTICAL);
        linearLayout.addView(linearLayout2, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        bottomButton = new TextView(context);
        bottomButton.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
        bottomButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        bottomButton.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.BOTTOM);
        bottomButton.setText(LocaleController.getString("YourEmailSkip", R.string.YourEmailSkip));
        bottomButton.setPadding(0, AndroidUtilities.dp(10), 0, 0);
        linearLayout2.addView(bottomButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.BOTTOM, 40, 0, 40, 14));
        bottomButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (type == 0) {
                    if (currentPassword.has_recovery) {
                        needShowProgress();
                        TLRPC.TL_auth_requestPasswordRecovery req = new TLRPC.TL_auth_requestPasswordRecovery();
                        ConnectionsManager.getInstance(currentAccount).sendRequest(req, new RequestDelegate() {
                            @Override
                            public void run(final TLObject response, final TLRPC.TL_error error) {
                                AndroidUtilities.runOnUIThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        needHideProgress();
                                        if (error == null) {
                                            final TLRPC.TL_auth_passwordRecovery res = (TLRPC.TL_auth_passwordRecovery) response;
                                            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                                            builder.setMessage(LocaleController.formatString("RestoreEmailSent", R.string.RestoreEmailSent, res.email_pattern));
                                            builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                                            builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), new DialogInterface.OnClickListener() {
                                                @Override
                                                public void onClick(DialogInterface dialogInterface, int i) {
                                                    TwoStepVerificationActivity fragment = new TwoStepVerificationActivity(currentAccount, 1);
                                                    fragment.currentPassword = currentPassword;
                                                    fragment.currentPassword.email_unconfirmed_pattern = res.email_pattern;
                                                    fragment.currentSecretId = currentSecretId;
                                                    fragment.currentSecret = currentSecret;
                                                    fragment.currentSecretSalt = currentSecretSalt;
                                                    fragment.passwordSetState = 4;
                                                    presentFragment(fragment);
                                                }
                                            });
                                            Dialog dialog = showDialog(builder.create());
                                            if (dialog != null) {
                                                dialog.setCanceledOnTouchOutside(false);
                                                dialog.setCancelable(false);
                                            }
                                        } else {
                                            if (error.text.startsWith("FLOOD_WAIT")) {
                                                int time = Utilities.parseInt(error.text);
                                                String timeString;
                                                if (time < 60) {
                                                    timeString = LocaleController.formatPluralString("Seconds", time);
                                                } else {
                                                    timeString = LocaleController.formatPluralString("Minutes", time / 60);
                                                }
                                                showAlertWithText(LocaleController.getString("AppName", R.string.AppName), LocaleController.formatString("FloodWaitTime", R.string.FloodWaitTime, timeString));
                                            } else {
                                                showAlertWithText(LocaleController.getString("AppName", R.string.AppName), error.text);
                                            }
                                        }
                                    }
                                });
                            }
                        }, ConnectionsManager.RequestFlagFailOnServerErrors | ConnectionsManager.RequestFlagWithoutLogin);
                    } else {
                        if (getParentActivity() == null) {
                            return;
                        }
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
                        builder.setNegativeButton(LocaleController.getString("RestorePasswordResetAccount", R.string.RestorePasswordResetAccount), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                Browser.openUrl(getParentActivity(), "https://telegram.org/deactivate?phone=" + UserConfig.getInstance(currentAccount).getClientPhone());
                            }
                        });
                        builder.setTitle(LocaleController.getString("RestorePasswordNoEmailTitle", R.string.RestorePasswordNoEmailTitle));
                        builder.setMessage(LocaleController.getString("RestorePasswordNoEmailText", R.string.RestorePasswordNoEmailText));
                        showDialog(builder.create());
                    }
                } else {
                    if (passwordSetState == 4) {
                        showAlertWithText(LocaleController.getString("RestorePasswordNoEmailTitle", R.string.RestorePasswordNoEmailTitle), LocaleController.getString("RestoreEmailTroubleText", R.string.RestoreEmailTroubleText));
                    } else {
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        builder.setMessage(LocaleController.getString("YourEmailSkipWarningText", R.string.YourEmailSkipWarningText));
                        builder.setTitle(LocaleController.getString("YourEmailSkipWarning", R.string.YourEmailSkipWarning));
                        builder.setPositiveButton(LocaleController.getString("YourEmailSkip", R.string.YourEmailSkip), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                email = "";
                                setNewPassword(false);
                            }
                        });
                        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                        showDialog(builder.create());
                    }
                }
            }
        });

        if (type == 0) {
            emptyView = new EmptyTextProgressView(context);
            emptyView.showProgress();
            frameLayout.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

            listView = new RecyclerListView(context);
            listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
            listView.setEmptyView(emptyView);
            listView.setVerticalScrollBarEnabled(false);
            frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            listView.setAdapter(listAdapter = new ListAdapter(context));
            listView.setOnItemClickListener(new RecyclerListView.OnItemClickListener() {
                @Override
                public void onItemClick(View view, int position) {
                    if (position == setPasswordRow || position == changePasswordRow) {
                        TwoStepVerificationActivity fragment = new TwoStepVerificationActivity(currentAccount, 1);
                        fragment.currentPasswordHash = currentPasswordHash;
                        fragment.currentPassword = currentPassword;
                        fragment.currentSecretId = currentSecretId;
                        fragment.currentSecret = currentSecret;
                        fragment.currentSecretSalt = currentSecretSalt;
                        presentFragment(fragment);
                    } else if (position == setRecoveryEmailRow || position == changeRecoveryEmailRow) {
                        TwoStepVerificationActivity fragment = new TwoStepVerificationActivity(currentAccount, 1);
                        fragment.currentPasswordHash = currentPasswordHash;
                        fragment.currentPassword = currentPassword;
                        fragment.currentSecretId = currentSecretId;
                        fragment.currentSecret = currentSecret;
                        fragment.currentSecretSalt = currentSecretSalt;
                        fragment.emailOnly = true;
                        fragment.passwordSetState = 3;
                        presentFragment(fragment);
                    } else if (position == turnPasswordOffRow || position == abortPasswordRow) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        String text = LocaleController.getString("TurnPasswordOffQuestion", R.string.TurnPasswordOffQuestion);
                        if (currentPassword.has_secure_values) {
                            text += "\n\n" + LocaleController.getString("TurnPasswordOffPassport", R.string.TurnPasswordOffPassport);
                        }
                        builder.setMessage(text);
                        builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                setNewPassword(true);
                            }
                        });
                        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                        showDialog(builder.create());
                    }
                }
            });

            updateRows();

            actionBar.setTitle(LocaleController.getString("TwoStepVerification", R.string.TwoStepVerification));
            titleTextView.setText(LocaleController.getString("PleaseEnterCurrentPassword", R.string.PleaseEnterCurrentPassword));
        } else if (type == 1) {
            setPasswordSetState(passwordSetState);
        }

        return fragmentView;
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.didSetTwoStepPassword) {
            if (args != null && args.length > 0 && args[0] != null) {
                currentPasswordHash = (byte[]) args[0];
                if (closeAfterSet) {
                    String email = (String) args[4];
                    if (TextUtils.isEmpty(email) && closeAfterSet) {
                        removeSelfFromStack();
                    }
                }
            }
            loadPasswordInfo(false);
            updateRows();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        paused = true;
    }

    @Override
    public void onResume() {
        super.onResume();
        paused = false;
        if (type == 1) {
            AndroidUtilities.runOnUIThread(new Runnable() {
                @Override
                public void run() {
                    if (passwordEditText != null) {
                        passwordEditText.requestFocus();
                        AndroidUtilities.showKeyboard(passwordEditText);
                    }
                }
            }, 200);
        }
        AndroidUtilities.requestAdjustResize(getParentActivity(), classGuid);
    }

    public void setCloseAfterSet(boolean value) {
        closeAfterSet = value;
    }

    public void setCurrentPasswordInfo(byte[] hash, TLRPC.account_Password password) {
        currentPasswordHash = hash;
        currentPassword = password;
    }

    @Override
    public void onTransitionAnimationEnd(boolean isOpen, boolean backward) {
        if (isOpen && type == 1) {
            AndroidUtilities.showKeyboard(passwordEditText);
        }
    }

    private void loadPasswordInfo(final boolean silent) {
        if (!silent) {
            loading = true;
            if (listAdapter != null) {
                listAdapter.notifyDataSetChanged();
            }
        }
        TLRPC.TL_account_getPassword req = new TLRPC.TL_account_getPassword();
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, new RequestDelegate() {
            @Override
            public void run(final TLObject response, final TLRPC.TL_error error) {
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        loading = false;
                        if (error == null) {
                            if (!silent) {
                                passwordEntered = currentPassword != null || response instanceof TLRPC.TL_account_noPassword;
                            }
                            currentPassword = (TLRPC.account_Password) response;
                            waitingForEmail = currentPassword.email_unconfirmed_pattern.length() > 0;
                            byte[] salt = new byte[currentPassword.new_salt.length + 8];
                            Utilities.random.nextBytes(salt);
                            System.arraycopy(currentPassword.new_salt, 0, salt, 0, currentPassword.new_salt.length);
                            currentPassword.new_salt = salt;

                            if (!paused && closeAfterSet && currentPassword instanceof TLRPC.TL_account_password) {
                                byte[] pendingCurrentSalt = currentPassword.current_salt;
                                byte[] pendingNewSecureSalt = currentPassword.new_secure_salt;
                                byte[] pendingSecureRandom = currentPassword.secure_random;
                                String pendingEmail = currentPassword.has_recovery ? "1" : null;
                                String pendingHint = currentPassword.hint;

                                if (!waitingForEmail && pendingCurrentSalt != null) {
                                    NotificationCenter.getInstance(currentAccount).removeObserver(TwoStepVerificationActivity.this, NotificationCenter.didSetTwoStepPassword);
                                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.didSetTwoStepPassword, null, pendingCurrentSalt, pendingNewSecureSalt, pendingSecureRandom, pendingEmail, pendingHint, null, null);
                                    finishFragment();
                                }
                            }
                        }
                        if (type == 0 && !destroyed && shortPollRunnable == null) {
                            shortPollRunnable = new Runnable() {
                                @Override
                                public void run() {
                                    if (shortPollRunnable == null) {
                                        return;
                                    }
                                    loadPasswordInfo(true);
                                    shortPollRunnable = null;
                                }
                            };
                            AndroidUtilities.runOnUIThread(shortPollRunnable, 5000);
                        }
                        updateRows();
                    }
                });
            }
        }, ConnectionsManager.RequestFlagFailOnServerErrors | ConnectionsManager.RequestFlagWithoutLogin);
    }

    private void setPasswordSetState(int state) {
        if (passwordEditText == null) {
            return;
        }
        passwordSetState = state;
        if (passwordSetState == 0) {
            actionBar.setTitle(LocaleController.getString("YourPassword", R.string.YourPassword));
            if (currentPassword instanceof TLRPC.TL_account_noPassword) {
                titleTextView.setText(LocaleController.getString("PleaseEnterFirstPassword", R.string.PleaseEnterFirstPassword));
            } else {
                titleTextView.setText(LocaleController.getString("PleaseEnterPassword", R.string.PleaseEnterPassword));
            }
            passwordEditText.setImeOptions(EditorInfo.IME_ACTION_NEXT);
            passwordEditText.setTransformationMethod(PasswordTransformationMethod.getInstance());
            bottomTextView.setVisibility(View.INVISIBLE);
            bottomButton.setVisibility(View.INVISIBLE);
        } else if (passwordSetState == 1) {
            actionBar.setTitle(LocaleController.getString("YourPassword", R.string.YourPassword));
            titleTextView.setText(LocaleController.getString("PleaseReEnterPassword", R.string.PleaseReEnterPassword));
            passwordEditText.setImeOptions(EditorInfo.IME_ACTION_NEXT);
            passwordEditText.setTransformationMethod(PasswordTransformationMethod.getInstance());
            bottomTextView.setVisibility(View.INVISIBLE);
            bottomButton.setVisibility(View.INVISIBLE);
        } else if (passwordSetState == 2) {
            actionBar.setTitle(LocaleController.getString("PasswordHint", R.string.PasswordHint));
            titleTextView.setText(LocaleController.getString("PasswordHintText", R.string.PasswordHintText));
            passwordEditText.setImeOptions(EditorInfo.IME_ACTION_NEXT);
            passwordEditText.setTransformationMethod(null);
            bottomTextView.setVisibility(View.INVISIBLE);
            bottomButton.setVisibility(View.INVISIBLE);
        } else if (passwordSetState == 3) {
            actionBar.setTitle(LocaleController.getString("RecoveryEmail", R.string.RecoveryEmail));
            titleTextView.setText(LocaleController.getString("YourEmail", R.string.YourEmail));
            passwordEditText.setImeOptions(EditorInfo.IME_ACTION_DONE);
            passwordEditText.setTransformationMethod(null);
            passwordEditText.setInputType(EditorInfo.TYPE_CLASS_TEXT | EditorInfo.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
            bottomTextView.setVisibility(View.VISIBLE);
            bottomButton.setVisibility(emailOnly ? View.INVISIBLE : View.VISIBLE);
        } else if (passwordSetState == 4) {
            actionBar.setTitle(LocaleController.getString("PasswordRecovery", R.string.PasswordRecovery));
            titleTextView.setText(LocaleController.getString("PasswordCode", R.string.PasswordCode));
            bottomTextView.setText(LocaleController.getString("RestoreEmailSentInfo", R.string.RestoreEmailSentInfo));
            bottomButton.setText(LocaleController.formatString("RestoreEmailTrouble", R.string.RestoreEmailTrouble, currentPassword.email_unconfirmed_pattern));
            passwordEditText.setImeOptions(EditorInfo.IME_ACTION_DONE);
            passwordEditText.setTransformationMethod(null);
            passwordEditText.setInputType(InputType.TYPE_CLASS_PHONE);
            bottomTextView.setVisibility(View.VISIBLE);
            bottomButton.setVisibility(View.VISIBLE);
        }
        passwordEditText.setText("");
    }

    private void updateRows() {
        rowCount = 0;
        setPasswordRow = -1;
        setPasswordDetailRow = -1;
        changePasswordRow = -1;
        turnPasswordOffRow = -1;
        setRecoveryEmailRow = -1;
        changeRecoveryEmailRow = -1;
        abortPasswordRow = -1;
        passwordSetupDetailRow = -1;
        passwordEnabledDetailRow = -1;
        passwordEmailVerifyDetailRow = -1;
        shadowRow = -1;
        if (!loading && currentPassword != null) {
            if (currentPassword instanceof TLRPC.TL_account_noPassword) {
                if (waitingForEmail) {
                    passwordSetupDetailRow = rowCount++;
                    abortPasswordRow = rowCount++;
                    shadowRow = rowCount++;
                } else {
                    setPasswordRow = rowCount++;
                    setPasswordDetailRow = rowCount++;
                }
            } else if (currentPassword instanceof TLRPC.TL_account_password) {
                changePasswordRow = rowCount++;
                turnPasswordOffRow = rowCount++;
                if (currentPassword.has_recovery) {
                    changeRecoveryEmailRow = rowCount++;
                } else {
                    setRecoveryEmailRow = rowCount++;
                }
                if (waitingForEmail) {
                    passwordEmailVerifyDetailRow = rowCount++;
                } else {
                    passwordEnabledDetailRow = rowCount++;
                }
            }
        }

        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
        if (passwordEntered) {
            if (listView != null) {
                listView.setVisibility(View.VISIBLE);
                scrollView.setVisibility(View.INVISIBLE);
                listView.setEmptyView(emptyView);
            }
            if (passwordEditText != null) {
                doneItem.setVisibility(View.GONE);
                passwordEditText.setVisibility(View.INVISIBLE);
                titleTextView.setVisibility(View.INVISIBLE);
                bottomTextView.setVisibility(View.INVISIBLE);
                bottomButton.setVisibility(View.INVISIBLE);
            }
        } else {
            if (listView != null) {
                listView.setEmptyView(null);
                listView.setVisibility(View.INVISIBLE);
                scrollView.setVisibility(View.VISIBLE);
                emptyView.setVisibility(View.INVISIBLE);
            }
            if (passwordEditText != null) {
                doneItem.setVisibility(View.VISIBLE);
                passwordEditText.setVisibility(View.VISIBLE);
                titleTextView.setVisibility(View.VISIBLE);
                bottomButton.setVisibility(View.VISIBLE);
                bottomTextView.setVisibility(View.INVISIBLE);
                bottomButton.setText(LocaleController.getString("ForgotPassword", R.string.ForgotPassword));
                if (currentPassword.hint != null && currentPassword.hint.length() > 0) {
                    passwordEditText.setHint(currentPassword.hint);
                } else {
                    passwordEditText.setHint("");
                }
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        if (!isFinishing() && !destroyed && passwordEditText != null) {
                            passwordEditText.requestFocus();
                            AndroidUtilities.showKeyboard(passwordEditText);
                        }
                    }
                }, 200);
            }
        }
    }

    private void needShowProgress() {
        if (getParentActivity() == null || getParentActivity().isFinishing() || progressDialog != null) {
            return;
        }
        progressDialog = new AlertDialog(getParentActivity(), 1);
        progressDialog.setMessage(LocaleController.getString("Loading", R.string.Loading));
        progressDialog.setCanceledOnTouchOutside(false);
        progressDialog.setCancelable(false);
        progressDialog.show();
    }

    private void needHideProgress() {
        if (progressDialog == null) {
            return;
        }
        try {
            progressDialog.dismiss();
        } catch (Exception e) {
            FileLog.e(e);
        }
        progressDialog = null;
    }

    private boolean isValidEmail(String text) {
        if (text == null || text.length() < 3) {
            return false;
        }
        int dot = text.lastIndexOf('.');
        int dog = text.lastIndexOf('@');
        return !(dot < 0 || dog < 0 || dot < dog);
    }

    private void showAlertWithText(String title, String text) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
        builder.setTitle(title);
        builder.setMessage(text);
        showDialog(builder.create());
    }

    private void setNewPassword(final boolean clear) {
        final TLRPC.TL_account_updatePasswordSettings req = new TLRPC.TL_account_updatePasswordSettings();
        req.current_password_hash = currentPasswordHash;
        req.new_settings = new TLRPC.TL_account_passwordInputSettings();
        if (clear) {
            UserConfig.getInstance(currentAccount).resetSavedPassword();
            if (waitingForEmail && currentPassword instanceof TLRPC.TL_account_noPassword) {
                req.new_settings.flags = 2;
                req.new_settings.email = "";
                req.current_password_hash = new byte[0];
            } else {
                req.new_settings.flags = 3;
                req.new_settings.hint = "";
                req.new_settings.new_password_hash = new byte[0];
                req.new_settings.new_salt = new byte[0];
                req.new_settings.email = "";
            }
        } else {
            if (firstPassword != null && firstPassword.length() > 0) {
                byte[] newPasswordBytes = AndroidUtilities.getStringBytes(firstPassword);

                byte[] new_salt = currentPassword.new_salt;
                byte[] hash = new byte[new_salt.length * 2 + newPasswordBytes.length];
                System.arraycopy(new_salt, 0, hash, 0, new_salt.length);
                System.arraycopy(newPasswordBytes, 0, hash, new_salt.length, newPasswordBytes.length);
                System.arraycopy(new_salt, 0, hash, hash.length - new_salt.length, new_salt.length);
                req.new_settings.flags |= 1;
                req.new_settings.hint = hint;
                req.new_settings.new_password_hash = Utilities.computeSHA256(hash, 0, hash.length);
                req.new_settings.new_salt = new_salt;

                if (currentSecret != null && currentSecret.length == 32) {
                    byte[] passwordHash = Utilities.computeSHA512(currentSecretSalt, newPasswordBytes, currentSecretSalt);
                    byte[] key = new byte[32];
                    System.arraycopy(passwordHash, 0, key, 0, 32);
                    byte[] iv = new byte[16];
                    System.arraycopy(passwordHash, 32, iv, 0, 16);

                    byte[] encryptedSecret = new byte[32];
                    System.arraycopy(currentSecret, 0, encryptedSecret, 0, 32);
                    Utilities.aesCbcEncryptionByteArraySafe(encryptedSecret, key, iv, 0, encryptedSecret.length, 0, 1);

                    req.new_settings.new_secure_secret = encryptedSecret;
                    req.new_settings.new_secure_salt = currentSecretSalt;
                    req.new_settings.new_secure_secret_id = currentSecretId;
                    req.new_settings.flags |= 4;
                }
            }
            if (email.length() > 0) {
                req.new_settings.flags |= 2;
                req.new_settings.email = email.trim();
            }
        }
        needShowProgress();
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, new RequestDelegate() {
            @Override
            public void run(final TLObject response, final TLRPC.TL_error error) {
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        needHideProgress();
                        if (error == null && response instanceof TLRPC.TL_boolTrue) {
                            if (clear) {
                                currentPassword = null;
                                currentPasswordHash = new byte[0];
                                loadPasswordInfo(false);
                                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.didRemovedTwoStepPassword);
                                updateRows();
                            } else {
                                if (getParentActivity() == null) {
                                    return;
                                }
                                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                                builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialogInterface, int i) {
                                        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.didSetTwoStepPassword, req.new_settings.new_password_hash, req.new_settings.new_salt, currentPassword.new_secure_salt, currentPassword.secure_random, email, hint, null, firstPassword);
                                        finishFragment();
                                    }
                                });
                                builder.setMessage(LocaleController.getString("YourPasswordSuccessText", R.string.YourPasswordSuccessText));
                                builder.setTitle(LocaleController.getString("YourPasswordSuccess", R.string.YourPasswordSuccess));
                                Dialog dialog = showDialog(builder.create());
                                if (dialog != null) {
                                    dialog.setCanceledOnTouchOutside(false);
                                    dialog.setCancelable(false);
                                }
                            }
                        } else if (error != null) {
                            if (error.text.equals("EMAIL_UNCONFIRMED")) {
                                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                                builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialogInterface, int i) {
                                        if (closeAfterSet) {
                                            TwoStepVerificationActivity activity = new TwoStepVerificationActivity(currentAccount, 0);
                                            activity.setCloseAfterSet(true);
                                            parentLayout.addFragmentToStack(activity, parentLayout.fragmentsStack.size() - 1);
                                        }
                                        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.didSetTwoStepPassword, req.new_settings.new_password_hash, req.new_settings.new_salt, currentPassword.new_secure_salt, currentPassword.secure_random, email, hint, email, firstPassword);
                                        finishFragment();
                                    }
                                });
                                builder.setMessage(LocaleController.getString("YourEmailAlmostThereText", R.string.YourEmailAlmostThereText));
                                builder.setTitle(LocaleController.getString("YourEmailAlmostThere", R.string.YourEmailAlmostThere));
                                Dialog dialog = showDialog(builder.create());
                                if (dialog != null) {
                                    dialog.setCanceledOnTouchOutside(false);
                                    dialog.setCancelable(false);
                                }
                            } else {
                                if (error.text.equals("EMAIL_INVALID")) {
                                    showAlertWithText(LocaleController.getString("AppName", R.string.AppName), LocaleController.getString("PasswordEmailInvalid", R.string.PasswordEmailInvalid));
                                } else if (error.text.startsWith("FLOOD_WAIT")) {
                                    int time = Utilities.parseInt(error.text);
                                    String timeString;
                                    if (time < 60) {
                                        timeString = LocaleController.formatPluralString("Seconds", time);
                                    } else {
                                        timeString = LocaleController.formatPluralString("Minutes", time / 60);
                                    }
                                    showAlertWithText(LocaleController.getString("AppName", R.string.AppName), LocaleController.formatString("FloodWaitTime", R.string.FloodWaitTime, timeString));
                                } else {
                                    showAlertWithText(LocaleController.getString("AppName", R.string.AppName), error.text);
                                }
                            }
                        }
                    }
                });
            }
        }, ConnectionsManager.RequestFlagFailOnServerErrors | ConnectionsManager.RequestFlagWithoutLogin);
    }

    private void checkSecretValues(byte[] passwordBytes, TLRPC.TL_account_passwordSettings passwordSettings) {
        if (passwordSettings.secure_secret.length == 32) {
            currentSecret = passwordSettings.secure_secret;
            currentSecretSalt = passwordSettings.secure_salt;
            currentSecretId = passwordSettings.secure_secret_id;

            byte[] passwordHash = Utilities.computeSHA512(currentSecretSalt, passwordBytes, currentSecretSalt);
            byte[] key = new byte[32];
            System.arraycopy(passwordHash, 0, key, 0, 32);
            byte[] iv = new byte[16];
            System.arraycopy(passwordHash, 32, iv, 0, 16);

            Utilities.aesCbcEncryptionByteArraySafe(currentSecret, key, iv, 0, currentSecret.length, 0, 0);
        } else {
            currentSecret = null;
            currentSecretSalt = null;
            currentSecretId = 0;
        }
    }

    private void processDone() {
        if (type == 0) {
            if (!passwordEntered) {
                String oldPassword = passwordEditText.getText().toString();
                if (oldPassword.length() == 0) {
                    onPasscodeError(false);
                    return;
                }
                final byte[] oldPasswordBytes = AndroidUtilities.getStringBytes(oldPassword);

                needShowProgress();
                byte[] hash = new byte[currentPassword.current_salt.length * 2 + oldPasswordBytes.length];
                System.arraycopy(currentPassword.current_salt, 0, hash, 0, currentPassword.current_salt.length);
                System.arraycopy(oldPasswordBytes, 0, hash, currentPassword.current_salt.length, oldPasswordBytes.length);
                System.arraycopy(currentPassword.current_salt, 0, hash, hash.length - currentPassword.current_salt.length, currentPassword.current_salt.length);

                final TLRPC.TL_account_getPasswordSettings req = new TLRPC.TL_account_getPasswordSettings();
                req.current_password_hash = Utilities.computeSHA256(hash, 0, hash.length);
                ConnectionsManager.getInstance(currentAccount).sendRequest(req, new RequestDelegate() {
                    @Override
                    public void run(final TLObject response, final TLRPC.TL_error error) {
                        AndroidUtilities.runOnUIThread(new Runnable() {
                            @Override
                            public void run() {
                                needHideProgress();
                                if (error == null) {
                                    checkSecretValues(oldPasswordBytes, (TLRPC.TL_account_passwordSettings) response);
                                    currentPasswordHash = req.current_password_hash;
                                    passwordEntered = true;
                                    AndroidUtilities.hideKeyboard(passwordEditText);
                                    updateRows();
                                } else {
                                    if (error.text.equals("PASSWORD_HASH_INVALID")) {
                                        onPasscodeError(true);
                                    } else if (error.text.startsWith("FLOOD_WAIT")) {
                                        int time = Utilities.parseInt(error.text);
                                        String timeString;
                                        if (time < 60) {
                                            timeString = LocaleController.formatPluralString("Seconds", time);
                                        } else {
                                            timeString = LocaleController.formatPluralString("Minutes", time / 60);
                                        }
                                        showAlertWithText(LocaleController.getString("AppName", R.string.AppName), LocaleController.formatString("FloodWaitTime", R.string.FloodWaitTime, timeString));
                                    } else {
                                        showAlertWithText(LocaleController.getString("AppName", R.string.AppName), error.text);
                                    }
                                }
                            }
                        });
                    }
                }, ConnectionsManager.RequestFlagFailOnServerErrors | ConnectionsManager.RequestFlagWithoutLogin);
            }
        } else if (type == 1) {
            if (passwordSetState == 0) {
                if (passwordEditText.getText().length() == 0) {
                    onPasscodeError(false);
                    return;
                }
                titleTextView.setText(LocaleController.getString("ReEnterYourPasscode", R.string.ReEnterYourPasscode));
                firstPassword = passwordEditText.getText().toString();
                setPasswordSetState(1);
            } else if (passwordSetState == 1) {
                if (!firstPassword.equals(passwordEditText.getText().toString())) {
                    try {
                        Toast.makeText(getParentActivity(), LocaleController.getString("PasswordDoNotMatch", R.string.PasswordDoNotMatch), Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                    onPasscodeError(true);
                    return;
                }
                setPasswordSetState(2);
            } else if (passwordSetState == 2) {
                hint = passwordEditText.getText().toString();
                if (hint.toLowerCase().equals(firstPassword.toLowerCase())) {
                    try {
                        Toast.makeText(getParentActivity(), LocaleController.getString("PasswordAsHintError", R.string.PasswordAsHintError), Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                    onPasscodeError(false);
                    return;
                }
                if (!currentPassword.has_recovery) {
                    setPasswordSetState(3);
                } else {
                    email = "";
                    setNewPassword(false);
                }
            } else if (passwordSetState == 3) {
                email = passwordEditText.getText().toString();
                if (!isValidEmail(email)) {
                    onPasscodeError(false);
                    return;
                }
                setNewPassword(false);
            } else if (passwordSetState == 4) {
                String code = passwordEditText.getText().toString();
                if (code.length() == 0) {
                    onPasscodeError(false);
                    return;
                }
                TLRPC.TL_auth_recoverPassword req = new TLRPC.TL_auth_recoverPassword();
                req.code = code;
                ConnectionsManager.getInstance(currentAccount).sendRequest(req, new RequestDelegate() {
                    @Override
                    public void run(TLObject response, final TLRPC.TL_error error) {
                        AndroidUtilities.runOnUIThread(new Runnable() {
                            @Override
                            public void run() {
                                if (error == null) {
                                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                                    builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialogInterface, int i) {
                                            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.didSetTwoStepPassword);
                                            finishFragment();
                                        }
                                    });
                                    builder.setMessage(LocaleController.getString("PasswordReset", R.string.PasswordReset));
                                    builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                                    Dialog dialog = showDialog(builder.create());
                                    if (dialog != null) {
                                        dialog.setCanceledOnTouchOutside(false);
                                        dialog.setCancelable(false);
                                    }
                                } else {
                                    if (error.text.startsWith("CODE_INVALID")) {
                                        onPasscodeError(true);
                                    } else if (error.text.startsWith("FLOOD_WAIT")) {
                                        int time = Utilities.parseInt(error.text);
                                        String timeString;
                                        if (time < 60) {
                                            timeString = LocaleController.formatPluralString("Seconds", time);
                                        } else {
                                            timeString = LocaleController.formatPluralString("Minutes", time / 60);
                                        }
                                        showAlertWithText(LocaleController.getString("AppName", R.string.AppName), LocaleController.formatString("FloodWaitTime", R.string.FloodWaitTime, timeString));
                                    } else {
                                        showAlertWithText(LocaleController.getString("AppName", R.string.AppName), error.text);
                                    }
                                }
                            }
                        });
                    }
                }, ConnectionsManager.RequestFlagFailOnServerErrors | ConnectionsManager.RequestFlagWithoutLogin);
            }
        }
    }

    private void onPasscodeError(boolean clear) {
        if (getParentActivity() == null) {
            return;
        }
        Vibrator v = (Vibrator) getParentActivity().getSystemService(Context.VIBRATOR_SERVICE);
        if (v != null) {
            v.vibrate(200);
        }
        if (clear) {
            passwordEditText.setText("");
        }
        AndroidUtilities.shakeView(titleTextView, 2, 0);
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int position = holder.getAdapterPosition();
            return position != setPasswordDetailRow && position != shadowRow && position != passwordSetupDetailRow && position != passwordEmailVerifyDetailRow && position != passwordEnabledDetailRow;
        }

        @Override
        public int getItemCount() {
            return loading || currentPassword == null ? 0 : rowCount;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0:
                    view = new TextSettingsCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 1:
                default:
                    view = new TextInfoPrivacyCell(mContext);
                    break;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 0:
                    TextSettingsCell textCell = (TextSettingsCell) holder.itemView;
                    textCell.setTag(Theme.key_windowBackgroundWhiteBlackText);
                    textCell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                    if (position == changePasswordRow) {
                        textCell.setText(LocaleController.getString("ChangePassword", R.string.ChangePassword), true);
                    } else if (position == setPasswordRow) {
                        textCell.setText(LocaleController.getString("SetAdditionalPassword", R.string.SetAdditionalPassword), true);
                    } else if (position == turnPasswordOffRow) {
                        textCell.setText(LocaleController.getString("TurnPasswordOff", R.string.TurnPasswordOff), true);
                    } else if (position == changeRecoveryEmailRow) {
                        textCell.setText(LocaleController.getString("ChangeRecoveryEmail", R.string.ChangeRecoveryEmail), abortPasswordRow != -1);
                    } else if (position == setRecoveryEmailRow) {
                        textCell.setText(LocaleController.getString("SetRecoveryEmail", R.string.SetRecoveryEmail), false);
                    } else if (position == abortPasswordRow) {
                        textCell.setTag(Theme.key_windowBackgroundWhiteRedText3);
                        textCell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteRedText3));
                        textCell.setText(LocaleController.getString("AbortPassword", R.string.AbortPassword), false);
                    }
                    break;
                case 1:
                    TextInfoPrivacyCell privacyCell = (TextInfoPrivacyCell) holder.itemView;
                    if (position == setPasswordDetailRow) {
                        privacyCell.setText(LocaleController.getString("SetAdditionalPasswordInfo", R.string.SetAdditionalPasswordInfo));
                        privacyCell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    } else if (position == shadowRow) {
                        privacyCell.setText("");
                        privacyCell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    } else if (position == passwordSetupDetailRow) {
                        privacyCell.setText(LocaleController.formatString("EmailPasswordConfirmText", R.string.EmailPasswordConfirmText, currentPassword.email_unconfirmed_pattern));
                        privacyCell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_top, Theme.key_windowBackgroundGrayShadow));
                    } else if (position == passwordEnabledDetailRow) {
                        privacyCell.setText(LocaleController.getString("EnabledPasswordText", R.string.EnabledPasswordText));
                        privacyCell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    } else if (position == passwordEmailVerifyDetailRow) {
                        privacyCell.setText(LocaleController.formatString("PendingEmailText", R.string.PendingEmailText, currentPassword.email_unconfirmed_pattern));
                        privacyCell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    }
                    break;
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == setPasswordDetailRow || position == shadowRow || position == passwordSetupDetailRow || position == passwordEnabledDetailRow || position == passwordEmailVerifyDetailRow) {
                return 1;
            }
            return 0;
        }
    }

    @Override
    public ThemeDescription[] getThemeDescriptions() {
        return new ThemeDescription[]{
                new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{TextSettingsCell.class}, null, null, null, Theme.key_windowBackgroundWhite),
                new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite),

                new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault),
                new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector),

                new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector),

                new ThemeDescription(listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider),

                new ThemeDescription(emptyView, ThemeDescription.FLAG_PROGRESSBAR, null, null, null, null, Theme.key_progressCircle),

                new ThemeDescription(listView, ThemeDescription.FLAG_CHECKTAG, new Class[]{TextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(listView, ThemeDescription.FLAG_CHECKTAG, new Class[]{TextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteRedText3),

                new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{TextInfoPrivacyCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow),
                new ThemeDescription(listView, 0, new Class[]{TextInfoPrivacyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText4),

                new ThemeDescription(titleTextView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteGrayText6),
                new ThemeDescription(bottomTextView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteGrayText6),
                new ThemeDescription(bottomButton, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlueText4),
                new ThemeDescription(passwordEditText, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(passwordEditText, ThemeDescription.FLAG_HINTTEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteHintText),
                new ThemeDescription(passwordEditText, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_windowBackgroundWhiteInputField),
                new ThemeDescription(passwordEditText, ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, null, null, null, null, Theme.key_windowBackgroundWhiteInputFieldActivated),
        };
    }
}
