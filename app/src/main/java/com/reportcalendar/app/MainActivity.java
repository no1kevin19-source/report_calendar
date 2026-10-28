package com.reportcalendar.app;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.google.firebase.FirebaseApp;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity {
    private static final int PHOTO_REQUEST_CODE = 4001;
    private static final String PREFS = "report_calendar_native";
    private static final String KEY_ASSIGNMENTS = "assignments";
    private static final String KEY_REMINDER_DAYS = "reminder_days";
    private static final String KEY_REMINDER_TIME = "reminder_time";
    private static final String FIRESTORE_ASSIGNMENTS_COLLECTION = "assignments";
    private static final String STATE_TITLE = "state_title";
    private static final String STATE_SUBJECT_INDEX = "state_subject_index";
    private static final String STATE_CUSTOM_SUBJECT = "state_custom_subject";
    private static final String STATE_PERIOD_INDEX = "state_period_index";
    private static final String STATE_DETAIL = "state_detail";
    private static final String STATE_DUE_MILLIS = "state_due_millis";
    private static final String STATE_PHOTOS = "state_photos";
    private static final String STATE_EDITING_ID = "state_editing_id";
    private static final String STATE_FORM_VISIBLE = "state_form_visible";
    private static final String STATE_REMINDER_VISIBLE = "state_reminder_visible";
    private static final String STATE_REMINDER_DRAFT_DAYS = "state_reminder_draft_days";
    private static final String STATE_REMINDER_DRAFT_TIME = "state_reminder_draft_time";
    private static final String STATE_VISIBLE_MONTH = "state_visible_month";
    private static final String STATE_SELECTED_DAY = "state_selected_day";

    private final List<Assignment> assignments = new ArrayList<>();
    private final List<Uri> selectedPhotos = new ArrayList<>();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy년 M월 d일 (E)", Locale.KOREAN);
    private final SimpleDateFormat shortDateFormat = new SimpleDateFormat("M월 d일", Locale.KOREAN);

    private SharedPreferences prefs;
    private FirebaseAuth firebaseAuth;
    private FirebaseFirestore firestoreDb;
    private ScrollView rootScrollView;
    private LinearLayout content;
    private View dashboardSection;
    private View weekSection;
    private View formSection;
    private View calendarSection;
    private View reminderSection;
    private LinearLayout formBody;
    private LinearLayout reminderBody;
    private ScrollView weekListScroll;
    private LinearLayout weekList;
    private GridLayout calendarGrid;
    private TextView monthTitle;
    private TextView nearestText;
    private TextView nearestMetaText;
    private LinearLayout nearestCard;
    private LinearLayout overdueCard;
    private TextView overdueText;
    private TextView overdueMetaText;
    private TextView brandSubtitle;
    private TextView formHeading;
    private TextView photoCountText;
    private HorizontalScrollView photoPreviewScroll;
    private LinearLayout photoPreviewList;
    private TextView reminderSummary;
    private LinearLayout reminderSystemSettings;
    private Button reminderMoreButton;
    private EditText titleInput;
    private EditText detailInput;
    private EditText customSubjectInput;
    private Spinner subjectSpinner;
    private Spinner periodSpinner;
    private Spinner visibilitySpinner;
    private TextView publicationStatus;
    private final Runnable publicationObserver = () -> {
        if (publicationStatus != null) publicationStatus.setText(AssignmentSync.get(this).status());
    };
    private TextView subjectErrorText;
    private TextView periodErrorText;
    private Spinner reminderDaySpinner;
    private Button dueDateButton;
    private Button formToggleButton;
    private Button formSaveButton;
    private Button formCancelButton;
    private Button reminderToggleButton;
    private Button reminderTimeButton;
    private Calendar selectedDueDate;
    private Calendar visibleMonth;
    private Calendar selectedCalendarDay;
    private Assignment editingAssignment;
    private String editingAssignmentId;
    private int reminderDraftDays;
    private String reminderDraftTime;
    private long lastTodayMillis;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        FirebaseApp.initializeApp(this);
        firebaseAuth = FirebaseAuth.getInstance();
        firestoreDb = FirebaseFirestore.getInstance();
        selectedDueDate = startOfToday();
        visibleMonth = startOfToday();
        visibleMonth.set(Calendar.DAY_OF_MONTH, 1);
        selectedCalendarDay = startOfToday();
        lastTodayMillis = startOfToday().getTimeInMillis();
        getWindow().setStatusBarColor(color(R.color.app_background));
        getWindow().setNavigationBarColor(color(R.color.app_background));
        loadAssignments();
        buildUi();
        if (savedInstanceState != null) {
            restoreTransientState(savedInstanceState);
        } else {
            loadReminderDraftFromSavedSettings();
            updateFormMode();
        }
        refreshUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        AuthController.get(this).foreground();
        AssignmentSync.get(this).observe(publicationObserver);
        AssignmentSync.get(this).retry();
        ReminderScheduler.reschedule(this);
        refreshReminderSummary();
        long todayMillis = startOfToday().getTimeInMillis();
        if (lastTodayMillis != 0 && todayMillis != lastTodayMillis) {
            lastTodayMillis = todayMillis;
            refreshHeaderSummary();
            refreshNearest();
            refreshWeek();
            refreshCalendar();
        }
    }

    @Override
    protected void onPause() {
        AssignmentSync.get(this).unobserve(publicationObserver);
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("state_visibility", visibilitySpinner.getSelectedItemPosition());
        outState.putString(STATE_TITLE, titleInput == null ? "" : titleInput.getText().toString());
        outState.putInt(STATE_SUBJECT_INDEX, subjectSpinner == null ? 0 : subjectSpinner.getSelectedItemPosition());
        outState.putString(STATE_CUSTOM_SUBJECT, customSubjectInput == null ? "" : customSubjectInput.getText().toString());
        outState.putInt(STATE_PERIOD_INDEX, periodSpinner == null ? 0 : periodSpinner.getSelectedItemPosition());
        outState.putString(STATE_DETAIL, detailInput == null ? "" : detailInput.getText().toString());
        outState.putLong(STATE_DUE_MILLIS, selectedDueDate == null ? startOfToday().getTimeInMillis() : selectedDueDate.getTimeInMillis());
        ArrayList<String> photoStrings = new ArrayList<>();
        for (Uri uri : selectedPhotos) {
            photoStrings.add(uri.toString());
        }
        outState.putStringArrayList(STATE_PHOTOS, photoStrings);
        outState.putString(STATE_EDITING_ID, editingAssignmentId);
        outState.putBoolean(STATE_FORM_VISIBLE, formBody != null && formBody.getVisibility() == View.VISIBLE);
        outState.putBoolean(STATE_REMINDER_VISIBLE, reminderBody != null && reminderBody.getVisibility() == View.VISIBLE);
        outState.putInt(STATE_REMINDER_DRAFT_DAYS, reminderDraftDays);
        outState.putString(STATE_REMINDER_DRAFT_TIME, reminderDraftTime);
        outState.putLong(STATE_VISIBLE_MONTH, visibleMonth == null ? startOfToday().getTimeInMillis() : visibleMonth.getTimeInMillis());
        outState.putLong(STATE_SELECTED_DAY, selectedCalendarDay == null ? startOfToday().getTimeInMillis() : selectedCalendarDay.getTimeInMillis());
    }

    private void buildUi() {
        rootScrollView = new ScrollView(this);
        rootScrollView.setFillViewport(true);
        rootScrollView.setClipToPadding(false);
        rootScrollView.setBackgroundColor(color(R.color.app_background));

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(42), dp(20), dp(40));
        rootScrollView.addView(content, new ScrollView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        content.addView(buildHeader());
        dashboardSection = buildDashboard();
        weekSection = buildWeekPanel();
        calendarSection = buildCalendarPanel();
        formSection = buildFormPanel();
        reminderSection = buildReminderPanel();
        content.addView(dashboardSection);
        content.addView(weekSection);
        content.addView(calendarSection);
        content.addView(formSection);
        content.addView(reminderSection);
        setContentView(rootScrollView);
    }

    private View buildHeader() {
        LinearLayout header = row();
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(8), dp(8), dp(8), dp(8));
        header.setBackground(roundRect(color(R.color.surface_primary), color(R.color.outline), dp(28)));
        header.setElevation(dp(1));
        LinearLayout.LayoutParams headerParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        headerParams.bottomMargin = dp(20);
        header.setLayoutParams(headerParams);

        TextView logo = text("D", 15, color(R.color.text_primary), Typeface.BOLD);
        logo.setGravity(Gravity.CENTER);
        logo.setBackground(circle(color(R.color.lavender_strong)));
        header.addView(logo, new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout brandCopy = new LinearLayout(this);
        brandCopy.setOrientation(LinearLayout.VERTICAL);
        TextView title = text("디데이 캘린더", 18, color(R.color.text_primary), Typeface.BOLD);
        brandSubtitle = text("이번 주 0개 · 예정 0개", 12, color(R.color.text_secondary), Typeface.NORMAL);
        brandSubtitle.setPadding(0, dp(3), 0, 0);
        brandCopy.addView(title);
        brandCopy.addView(brandSubtitle);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        titleParams.leftMargin = dp(14);
        header.addView(brandCopy, titleParams);

        Button login = secondaryButton("로그인");
        login.setContentDescription("로그인 화면 열기");
        login.setSingleLine(true);
        login.setPadding(dp(8), dp(4), dp(8), dp(4));
        login.setMinWidth(dp(48));
        login.setMinimumWidth(dp(48));
        login.setBackground(pill(color(R.color.lavender_soft), 0));
        login.setOnClickListener(view -> startActivity(new Intent(this, AuthActivity.class)));
        LinearLayout.LayoutParams loginParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        loginParams.leftMargin = dp(8);
        loginParams.rightMargin = dp(4);
        header.addView(login, loginParams);

        ImageButton menu = iconImageButton(R.drawable.ic_menu, "메뉴 열기", color(R.color.surface_primary), color(R.color.text_primary));
        menu.setOnClickListener(view -> showNavigationMenu());
        header.addView(menu, new LinearLayout.LayoutParams(dp(48), dp(48)));

        return header;
    }

    private View buildDashboard() {
        LinearLayout dashboard = new LinearLayout(this);
        dashboard.setOrientation(LinearLayout.VERTICAL);
        dashboard.setPadding(0, 0, 0, dp(22));

        nearestCard = surfaceCard(color(R.color.lavender_soft), 0);
        nearestCard.setClickable(true);
        nearestCard.setFocusable(true);
        nearestCard.addView(eyebrow("다가오는 수행평가"));
        nearestText = text("", 21, color(R.color.text_primary), Typeface.BOLD);
        nearestCard.addView(nearestText);
        nearestMetaText = text("", 13, color(R.color.text_secondary), Typeface.NORMAL);
        nearestMetaText.setPadding(0, dp(8), 0, 0);
        nearestCard.addView(nearestMetaText);
        dashboard.addView(nearestCard);

        overdueCard = surfaceCard(color(R.color.danger_soft), 0);
        overdueCard.setClickable(true);
        overdueCard.setFocusable(true);
        overdueCard.addView(eyebrow("확인 필요"));
        overdueText = text("기한 지난 과제 0개", 19, color(R.color.danger), Typeface.BOLD);
        overdueCard.addView(overdueText);
        overdueMetaText = text("눌러서 지난 과제를 확인해요.", 13, color(R.color.text_secondary), Typeface.NORMAL);
        overdueMetaText.setPadding(0, dp(8), 0, 0);
        overdueCard.addView(overdueMetaText);
        overdueCard.setVisibility(View.GONE);
        dashboard.addView(overdueCard);

        TextView quickTitle = text("빠른 기능", 18, color(R.color.text_primary), Typeface.BOLD);
        quickTitle.setPadding(0, dp(8), 0, dp(12));
        dashboard.addView(quickTitle);

        GridLayout quickActions = new GridLayout(this);
        quickActions.setColumnCount(2);
        quickActions.setRowCount(2);
        quickActions.setUseDefaultMargins(false);
        LinearLayout.LayoutParams quickParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        dashboard.addView(quickActions, quickParams);

        quickActions.addView(
            quickActionCard(R.drawable.ic_assignment, "수행평가 등록", "새 일정을 추가해요", color(R.color.lavender), color(R.color.text_primary), this::openFormAndScroll),
            quickActionParams(0, 0)
        );
        quickActions.addView(
            quickActionCard(R.drawable.ic_week, "이번 주", "이번 주 마감 확인", color(R.color.butter_yellow), color(R.color.text_primary), () -> scrollTo(weekSection)),
            quickActionParams(dp(8), 0)
        );
        quickActions.addView(
            quickActionCard(R.drawable.ic_calendar, "캘린더", "월간 일정 한눈에", color(R.color.charcoal), Color.WHITE, () -> scrollTo(calendarSection)),
            quickActionParams(0, dp(8))
        );
        quickActions.addView(
            quickActionCard(R.drawable.ic_notifications, "알림 설정", "마감 전에 알려드려요", color(R.color.surface_primary), color(R.color.text_primary), this::openReminderAndScroll),
            quickActionParams(dp(8), dp(8))
        );

        return dashboard;
    }

    private View buildFormPanel() {
        LinearLayout panel = card();
        panel.addView(eyebrow("일정 관리"));
        LinearLayout head = row();
        head.setGravity(Gravity.CENTER_VERTICAL);
        formHeading = sectionTitle("수행평가 등록");
        head.addView(formHeading, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        formToggleButton = compactButton("열기");
        head.addView(formToggleButton);
        panel.addView(head);

        formBody = new LinearLayout(this);
        formBody.setOrientation(LinearLayout.VERTICAL);
        formBody.setVisibility(View.GONE);
        formToggleButton.setOnClickListener(view -> {
            boolean opening = formBody.getVisibility() != View.VISIBLE;
            formBody.setVisibility(opening ? View.VISIBLE : View.GONE);
            formToggleButton.setText(opening ? "접기" : "열기");
        });

        titleInput = editText("과제명");
        formBody.addView(label("과제명"));
        formBody.addView(titleInput);

        subjectSpinner = spinner(new String[]{"선택", "국어", "영어", "수학", "사회", "과학", "역사", "도덕", "기술가정", "정보", "체육", "음악", "미술", "직접 입력"});
        formBody.addView(label("과목"));
        formBody.addView(subjectSpinner);
        subjectErrorText = errorText("과목을 선택해 주세요");
        formBody.addView(subjectErrorText);

        customSubjectInput = editText("직접 과목 입력");
        customSubjectInput.setVisibility(View.GONE);
        formBody.addView(customSubjectInput);
        subjectSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                customSubjectInput.setVisibility("직접 입력".equals(subjectSpinner.getSelectedItem().toString()) ? View.VISIBLE : View.GONE);
                subjectErrorText.setVisibility(View.GONE);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        periodSpinner = spinner(new String[]{"선택", "1교시", "2교시", "3교시", "4교시", "5교시", "6교시", "7교시"});
        formBody.addView(label("교시"));
        formBody.addView(periodSpinner);
        periodErrorText = errorText("교시를 선택해 주세요");
        formBody.addView(periodErrorText);
        periodSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                periodErrorText.setVisibility(View.GONE);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        detailInput = editText("세부내용");
        detailInput.setMinLines(3);
        detailInput.setMinHeight(dp(112));
        detailInput.setPadding(dp(16), dp(14), dp(16), dp(14));
        detailInput.setGravity(Gravity.TOP | Gravity.START);
        formBody.addView(label("세부내용"));
        formBody.addView(detailInput);

        dueDateButton = secondaryButton("마감일 선택");
        dueDateButton.setOnClickListener(view -> pickDate());
        formBody.addView(label("마감일"));
        formBody.addView(dueDateButton);

        formBody.addView(label("공개 범위"));
        visibilitySpinner = spinner(new String[]{"비공개 · 내 휴대폰에만 저장", "공개 · 로그인한 모든 사용자에게 보이기"});
        formBody.addView(visibilitySpinner);
        TextView sharingHelp = text("공개하면 로그인한 모든 사용자가 과제 내용과 완료 여부를 볼 수 있어요. 사진은 공유되지 않아요.", 13, color(R.color.text_secondary), Typeface.NORMAL);
        sharingHelp.setPadding(0, dp(8), 0, dp(12));
        formBody.addView(sharingHelp);
        publicationStatus = text("", 13, color(R.color.text_secondary), Typeface.NORMAL);
        formBody.addView(publicationStatus);
        Button retryPublication = secondaryButton("공개 과제 저장·삭제 다시 시도");
        retryPublication.setOnClickListener(view -> AssignmentSync.get(this).retry());
        formBody.addView(retryPublication);

        Button photoButton = secondaryButton("사진 업로드");
        photoButton.setCompoundDrawablesWithIntrinsicBounds(tintedDrawable(R.drawable.ic_photo, color(R.color.text_primary)), null, null, null);
        photoButton.setCompoundDrawablePadding(dp(8));
        photoButton.setOnClickListener(view -> pickPhotos());
        formBody.addView(label("첨부사진"));
        formBody.addView(photoButton);

        photoCountText = text("사진을 여러 장 첨부할 수 있어요", 13, color(R.color.text_secondary), Typeface.NORMAL);
        photoCountText.setPadding(0, dp(6), 0, dp(12));
        formBody.addView(photoCountText);

        photoPreviewScroll = new HorizontalScrollView(this);
        photoPreviewScroll.setHorizontalScrollBarEnabled(false);
        photoPreviewScroll.setVisibility(View.GONE);
        photoPreviewList = row();
        photoPreviewScroll.addView(photoPreviewList);
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        previewParams.bottomMargin = dp(14);
        formBody.addView(photoPreviewScroll, previewParams);

        LinearLayout formActions = row();
        formSaveButton = primaryButton("등록하기");
        formSaveButton.setOnClickListener(view -> saveAssignmentFromForm());
        formActions.addView(formSaveButton, new LinearLayout.LayoutParams(0, dp(52), 1));

        formCancelButton = secondaryButton("수정 취소");
        formCancelButton.setVisibility(View.GONE);
        formCancelButton.setOnClickListener(view -> cancelFormEditing());
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(0, dp(52), 1);
        cancelParams.leftMargin = dp(8);
        formActions.addView(formCancelButton, cancelParams);
        formBody.addView(formActions);

        panel.addView(formBody);

        return panel;
    }

    private View buildWeekPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18), dp(18), dp(18), dp(18));
        panel.setBackground(roundRect(color(R.color.lavender_soft), 0, dp(22)));
        LinearLayout.LayoutParams panelParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        panelParams.topMargin = dp(16);
        panelParams.bottomMargin = dp(24);
        panel.setLayoutParams(panelParams);

        panel.addView(sectionTitle("이번 주 수행평가"));
        weekListScroll = new ScrollView(this);
        weekListScroll.setFillViewport(false);
        weekListScroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        weekListScroll.setVerticalScrollBarEnabled(false);
        weekList = new LinearLayout(this);
        weekList.setOrientation(LinearLayout.VERTICAL);
        weekListScroll.addView(weekList, new ScrollView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        panel.addView(weekListScroll, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(292)
        ));
        return panel;
    }

    private View buildCalendarPanel() {
        LinearLayout panel = card();
        panel.addView(eyebrow("캘린더"));
        panel.addView(sectionTitle("마감일 보기"));

        LinearLayout controls = row();
        controls.setGravity(Gravity.CENTER_VERTICAL);

        ImageButton prev = iconImageButton(R.drawable.ic_chevron_left, "이전 달", color(R.color.surface_soft), color(R.color.text_primary));
        prev.setOnClickListener(view -> {
            visibleMonth.add(Calendar.MONTH, -1);
            refreshCalendar();
        });
        controls.addView(prev, new LinearLayout.LayoutParams(dp(44), dp(44)));

        monthTitle = text("", 17, color(R.color.text_primary), Typeface.BOLD);
        monthTitle.setGravity(Gravity.CENTER);
        controls.addView(monthTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        ImageButton next = iconImageButton(R.drawable.ic_chevron_right, "다음 달", color(R.color.surface_soft), color(R.color.text_primary));
        next.setOnClickListener(view -> {
            visibleMonth.add(Calendar.MONTH, 1);
            refreshCalendar();
        });
        controls.addView(next, new LinearLayout.LayoutParams(dp(44), dp(44)));
        panel.addView(controls);
        calendarGrid = new GridLayout(this);
        calendarGrid.setColumnCount(7);
        calendarGrid.setUseDefaultMargins(false);
        panel.addView(calendarGrid, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        return panel;
    }

    private View buildReminderPanel() {
        LinearLayout panel = card();
        panel.addView(eyebrow("알림 설정"));

        LinearLayout head = row();
        head.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(text("다가오는 수행평가 알림", 19, color(R.color.text_primary), Typeface.BOLD));
        TextView description = text("마감 전에 휴대폰 알림으로 알려드려요.", 13, color(R.color.text_secondary), Typeface.NORMAL);
        description.setPadding(0, dp(4), dp(8), 0);
        copy.addView(description);
        head.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        reminderToggleButton = compactButton("열기");
        head.addView(reminderToggleButton);
        panel.addView(head);

        reminderBody = new LinearLayout(this);
        reminderBody.setOrientation(LinearLayout.VERTICAL);
        reminderBody.setPadding(0, dp(16), 0, 0);
        reminderBody.setVisibility(View.GONE);
        reminderToggleButton.setOnClickListener(view -> {
            boolean opening = reminderBody.getVisibility() != View.VISIBLE;
            reminderBody.setVisibility(opening ? View.VISIBLE : View.GONE);
            reminderToggleButton.setText(opening ? "접기" : "열기");
        });

        TextView preparing = text("휴대폰 알림", 12, color(R.color.text_primary), Typeface.BOLD);
        preparing.setPadding(dp(10), dp(5), dp(10), dp(5));
        preparing.setBackground(pill(color(R.color.butter_yellow), 0));
        LinearLayout.LayoutParams preparingParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        preparingParams.bottomMargin = dp(10);
        reminderBody.addView(preparing, preparingParams);

        TextView notice = text("이 휴대폰에 저장된 미완료 과제를 선택한 날짜·시간에 한 번 알려드려요. 같은 시간의 과제는 한 알림으로 묶어요. 이미 지난 알림 시간은 건너뛰어요.", 13, color(R.color.text_secondary), Typeface.NORMAL);
        notice.setPadding(dp(14), dp(12), dp(14), dp(12));
        notice.setBackground(roundRect(color(R.color.surface_soft), 0, dp(16)));
        reminderBody.addView(notice);

        reminderDaySpinner = spinner(new String[]{"1일 전", "2일 전", "3일 전", "7일 전"});
        reminderDaySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                reminderDraftDays = position;
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        reminderBody.addView(label("마감 며칠 전에 받을까요?"));
        reminderBody.addView(reminderDaySpinner);

        reminderTimeButton = secondaryButton("알림 시간 선택");
        reminderTimeButton.setOnClickListener(view -> pickReminderTime());
        reminderBody.addView(label("알림 시간"));
        reminderBody.addView(reminderTimeButton);

        LinearLayout actions = row();
        Button save = primaryButton("알림 켜기·저장");
        save.setOnClickListener(view -> {
            if (reminderDraftTime == null || reminderDraftTime.isEmpty()) {
                Toast.makeText(this, "알림 시간을 먼저 선택해 주세요", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!prefs.edit().putInt(KEY_REMINDER_DAYS, reminderDraftDays)
                    .putString(KEY_REMINDER_TIME, reminderDraftTime)
                    .putBoolean(ReminderScheduler.ENABLED, true).commit()) {
                Toast.makeText(this, "설정을 저장하지 못했어요. 저장 공간을 확인해 주세요", Toast.LENGTH_LONG).show();
                return;
            }
            ReminderScheduler.reschedule(this);
            requestReminderPermission();
            Toast.makeText(this, "알림 설정을 저장했어요. 아래에서 알림 상태를 확인해 주세요", Toast.LENGTH_LONG).show();
            refreshReminderSummary();
        });
        actions.addView(save, new LinearLayout.LayoutParams(0, dp(52), 1));

        Button cancel = secondaryButton("취소");
        cancel.setOnClickListener(view -> {
            loadReminderDraftFromSavedSettings();
            Toast.makeText(this, "변경을 취소했어요. 이전 설정을 유지해요.", Toast.LENGTH_SHORT).show();
            refreshReminderSummary();
        });
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(0, dp(52), 1);
        cancelParams.leftMargin = dp(8);
        actions.addView(cancel, cancelParams);
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        actionParams.topMargin = dp(12);
        reminderBody.addView(actions, actionParams);

        reminderSummary = text("", 13, color(R.color.text_secondary), Typeface.NORMAL);
        reminderSummary.setPadding(dp(14), dp(12), dp(14), dp(12));
        reminderSummary.setBackground(roundRect(color(R.color.lavender_soft), 0, dp(16)));
        LinearLayout.LayoutParams summaryParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        summaryParams.topMargin = dp(16);
        reminderBody.addView(reminderSummary, summaryParams);

        // Keep frequent actions visible; system permissions are secondary, expandable controls.
        LinearLayout utilityActions = row();
        LinearLayout.LayoutParams utilityParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        utilityParams.topMargin = dp(16);
        reminderBody.addView(utilityActions, utilityParams);
        Button disable = secondaryButton("알림 끄기");
        disable.setOnClickListener(v -> {
            prefs.edit().putBoolean(ReminderScheduler.ENABLED, false).commit();
            ReminderScheduler.reschedule(this);
            getSystemService(android.app.NotificationManager.class).cancel(8202);
            refreshReminderSummary();
        });
        Button test = outlinedButton("알림 테스트");
        test.setContentDescription("테스트 알림 보내기");
        test.setOnClickListener(v -> {
            if (!ReminderScheduler.test(this)) requestReminderPermission();
            else Toast.makeText(this, "휴대폰의 알림창을 확인해 주세요", Toast.LENGTH_SHORT).show();
        });
        utilityActions.addView(test, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        LinearLayout.LayoutParams disableParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        disableParams.leftMargin = dp(12);
        utilityActions.addView(disable, disableParams);

        LinearLayout systemSettings = new LinearLayout(this);
        reminderSystemSettings = systemSettings;
        systemSettings.setOrientation(LinearLayout.VERTICAL);
        systemSettings.setPadding(dp(12), dp(12), dp(12), dp(12));
        systemSettings.setBackground(roundRect(color(R.color.surface_soft), 0, dp(18)));
        systemSettings.setVisibility(prefs.getBoolean(ReminderScheduler.ENABLED, false)
            && !ReminderScheduler.allowed(this) ? View.VISIBLE : View.GONE);
        Button more = outlinedButton(systemSettings.getVisibility() == View.VISIBLE ? "추가 설정 접기" : "추가 설정 보기");
        reminderMoreButton = more;
        more.setContentDescription("휴대폰 알림 권한과 정확한 시간 설정 "
            + (systemSettings.getVisibility() == View.VISIBLE ? "접기" : "펼치기"));
        more.setOnClickListener(v -> {
            boolean opening = systemSettings.getVisibility() != View.VISIBLE;
            systemSettings.setVisibility(opening ? View.VISIBLE : View.GONE);
            more.setText(opening ? "추가 설정 접기" : "추가 설정 보기");
            more.setContentDescription("휴대폰 알림 권한과 정확한 시간 설정 " + (opening ? "접기" : "펼치기"));
        });
        LinearLayout.LayoutParams moreParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        moreParams.topMargin = dp(16);
        reminderBody.addView(more, moreParams);
        LinearLayout.LayoutParams systemParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        systemParams.topMargin = dp(12);
        reminderBody.addView(systemSettings, systemParams);
        TextView permissionHelp = text("알림이 안 오거나 늦게 올 때 확인해 주세요.", 13, color(R.color.text_secondary), Typeface.NORMAL);
        permissionHelp.setPadding(dp(2), 0, dp(2), dp(12));
        systemSettings.addView(permissionHelp);
        Button permission = secondaryButton("휴대폰 알림 설정 열기");
        permission.setBackground(pill(color(R.color.surface_primary), 0));
        permission.setOnClickListener(v -> openNotificationSettings());
        systemSettings.addView(permission, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            Button exact = secondaryButton("정확한 시간 알림 허용");
            exact.setOnClickListener(v -> {
                try {
                    startActivity(new Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:" + getPackageName())));
                } catch (android.content.ActivityNotFoundException e) {
                    Toast.makeText(this, "휴대폰 설정에서 ‘알람 및 리마인더’를 확인해 주세요", Toast.LENGTH_LONG).show();
                }
            });
            exact.setBackground(pill(color(R.color.surface_primary), 0));
            LinearLayout.LayoutParams exactParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            exactParams.topMargin = dp(12);
            systemSettings.addView(exact, exactParams);
        }
        loadReminderDraftFromSavedSettings();
        panel.addView(reminderBody);
        return panel;
    }

    private void saveAssignmentFromForm() {
        String title = titleInput.getText().toString().trim();
        boolean hasMissingRequiredField = false;
        View firstMissingField = null;
        if (title.isEmpty()) {
            titleInput.setError("과제명을 입력해 주세요");
            hasMissingRequiredField = true;
            firstMissingField = titleInput;
        }

        String subject = subjectSpinner.getSelectedItem().toString();
        if ("선택".equals(subject)) {
            subjectErrorText.setVisibility(View.VISIBLE);
            hasMissingRequiredField = true;
            if (firstMissingField == null) {
                firstMissingField = subjectSpinner;
            }
        } else if ("직접 입력".equals(subject)) {
            subject = customSubjectInput.getText().toString().trim();
            if (subject.isEmpty()) {
                customSubjectInput.setError("과목을 입력해 주세요");
                hasMissingRequiredField = true;
                if (firstMissingField == null) {
                    firstMissingField = customSubjectInput;
                }
            }
        }

        String period = periodSpinner.getSelectedItem().toString();
        if ("선택".equals(period)) {
            periodErrorText.setVisibility(View.VISIBLE);
            hasMissingRequiredField = true;
            if (firstMissingField == null) {
                firstMissingField = periodSpinner;
            }
        }

        if (hasMissingRequiredField) {
            if (firstMissingField != null) {
                firstMissingField.requestFocus();
            }
            Toast.makeText(this, "입력하지 않은 항목을 확인해 주세요", Toast.LENGTH_SHORT).show();
            return;
        }

        Assignment assignment;
        boolean isNewAssignment = editingAssignmentId == null;
        if (isNewAssignment) {
            assignment = new Assignment();
            assignment.id = java.util.UUID.randomUUID().toString();
        } else {
            assignment = findAssignmentById(editingAssignmentId);
            if (assignment == null) {
                Toast.makeText(this, "수정할 수행평가를 찾을 수 없습니다", Toast.LENGTH_SHORT).show();
                editingAssignment = null;
                editingAssignmentId = null;
                updateFormMode();
                return;
            }
        }
        boolean publicSelected = visibilitySpinner.getSelectedItemPosition() == 1;
        detachUnpublished(assignment);
        FirebaseUser publisher = firebaseAuth.getCurrentUser();
        if ((!assignment.cloudId.isEmpty() || publicSelected) &&
                (publisher == null || publisher.isAnonymous()
                 || !AssignmentPublication.canWrite(assignment.ownerUid, publisher.getUid())
                 || publisher.getUid().equals(DeletionJournal.pendingUid(this)))) {
            Toast.makeText(this, "공개 과제는 작성한 계정으로 로그인한 후 저장해 주세요", Toast.LENGTH_LONG).show();
            return;
        }
        if (publicSelected) {
            try {
                AssignmentPublication.fields(assignment.id, publisher.getUid(), title, subject, period,
                    detailInput.getText().toString().trim(), selectedDueDate.getTimeInMillis(), assignment.complete);
            } catch (IllegalArgumentException error) {
                Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show(); return;
            }
            if (assignment.cloudId.isEmpty()) assignment.cloudId = java.util.UUID.randomUUID().toString();
            assignment.ownerUid = publisher.getUid();
        }
        assignment.isPublic = publicSelected;
        assignment.title = title;
        assignment.subject = subject;
        assignment.detail = detailInput.getText().toString().trim();
        assignment.period = period;
        assignment.dueMillis = selectedDueDate.getTimeInMillis();
        assignment.photos.clear();
        for (Uri uri : selectedPhotos) {
            assignment.photos.add(uri.toString());
        }

        if (isNewAssignment) {
            assignments.add(assignment);
        }

        if (!savePublication(assignment, false)) {
            loadAssignments();
            Toast.makeText(this, "저장하지 못했어요. 입력 내용은 유지되니 다시 시도해 주세요", Toast.LENGTH_LONG).show();
            return;
        }
        editingAssignment = null;
        editingAssignmentId = null;
        clearForm();
        updateFormMode();
        refreshUi();
        Toast.makeText(this, assignment.cloudId.isEmpty() ? "휴대폰에 저장했어요" : "휴대폰에 저장했어요. 공개 여부는 ‘공개 범위’ 아래 안내를 확인해 주세요", Toast.LENGTH_LONG).show();
    }

    private void pickDate() {
        new DatePickerDialog(
            this,
            (view, year, month, dayOfMonth) -> {
                selectedDueDate.set(year, month, dayOfMonth, 0, 0, 0);
                selectedDueDate.set(Calendar.MILLISECOND, 0);
                dueDateButton.setText(dateFormat.format(selectedDueDate.getTime()));
            },
            selectedDueDate.get(Calendar.YEAR),
            selectedDueDate.get(Calendar.MONTH),
            selectedDueDate.get(Calendar.DAY_OF_MONTH)
        ).show();
    }

    private void pickReminderTime() {
        Calendar now = Calendar.getInstance();
        int initialHour = now.get(Calendar.HOUR_OF_DAY);
        int initialMinute = now.get(Calendar.MINUTE);
        if (reminderDraftTime != null && reminderDraftTime.matches("\\d{2}:\\d{2}")) {
            try {
                initialHour = Integer.parseInt(reminderDraftTime.substring(0, 2));
                initialMinute = Integer.parseInt(reminderDraftTime.substring(3, 5));
            } catch (NumberFormatException ignored) {
                initialHour = now.get(Calendar.HOUR_OF_DAY);
                initialMinute = now.get(Calendar.MINUTE);
            }
        }
        new TimePickerDialog(
            this,
            (view, hourOfDay, minute) -> {
                reminderDraftTime = String.format(Locale.KOREAN, "%02d:%02d", hourOfDay, minute);
                updateReminderTimeButton();
            },
            initialHour,
            initialMinute,
            true
        ).show();
    }

    private void pickPhotos() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(Intent.createChooser(intent, getString(R.string.photo_chooser_title)), PHOTO_REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PHOTO_REQUEST_CODE || resultCode != RESULT_OK || data == null) {
            return;
        }

        ClipData clipData = data.getClipData();
        if (clipData != null) {
            for (int index = 0; index < clipData.getItemCount(); index++) {
                persistAndAddPhoto(clipData.getItemAt(index).getUri());
            }
        } else if (data.getData() != null) {
            persistAndAddPhoto(data.getData());
        }
        refreshPhotoStatusText();
        refreshPhotoPreview();
    }

    private void persistAndAddPhoto(Uri uri) {
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {
            // Some providers do not allow persistable grants.
        }
        selectedPhotos.add(uri);
    }

    private void refreshPhotoPreview() {
        if (photoPreviewList == null || photoPreviewScroll == null) {
            return;
        }
        photoPreviewList.removeAllViews();
        photoPreviewScroll.setVisibility(selectedPhotos.isEmpty() ? View.GONE : View.VISIBLE);
        for (Uri uri : selectedPhotos) {
            FrameLayout thumbnail = new FrameLayout(this);
            thumbnail.setPadding(0, 0, dp(8), 0);

            ImageView image = new ImageView(this);
            image.setImageURI(uri);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setContentDescription("선택한 사진 미리보기");
            image.setBackground(roundRect(color(R.color.surface_soft), 0, dp(16)));
            image.setClipToOutline(true);
            image.setOnClickListener(view -> openPhoto(uri));
            thumbnail.addView(image, new FrameLayout.LayoutParams(dp(88), dp(88)));

            TextView removeButton = photoRemoveButton();
            FrameLayout removeTarget = iconTouchTarget(removeButton, dp(24), "첨부사진 삭제");
            removeTarget.setOnClickListener(view -> removeSelectedPhoto(uri));
            FrameLayout.LayoutParams removeParams = new FrameLayout.LayoutParams(dp(48), dp(48));
            removeParams.gravity = Gravity.TOP | Gravity.RIGHT;
            removeParams.topMargin = dp(-9);
            removeParams.rightMargin = dp(-1);
            thumbnail.addView(removeTarget, removeParams);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(96), dp(88));
            params.rightMargin = dp(8);
            photoPreviewList.addView(thumbnail, params);
        }
    }

    private void removeSelectedPhoto(Uri uri) {
        selectedPhotos.remove(uri);
        refreshPhotoStatusText();
        refreshPhotoPreview();
    }

    private void refreshPhotoStatusText() {
        if (photoCountText == null) {
            return;
        }
        photoCountText.setText(selectedPhotos.isEmpty() ? "사진을 여러 장 첨부할 수 있어요" : selectedPhotos.size() + "장 선택됨");
    }

    private void refreshUi() {
        refreshHeaderSummary();
        refreshNearest();
        refreshWeek();
        refreshCalendar();
        refreshReminderSummary();
        dueDateButton.setText(dateFormat.format(selectedDueDate.getTime()));
    }

    private void refreshNearest() {
        Assignment nearestUpcoming = null;
        List<Assignment> overdueItems = new ArrayList<>();
        long today = startOfToday().getTimeInMillis();
        for (Assignment assignment : assignments) {
            if (assignment.complete) {
                continue;
            }
            if (assignment.dueMillis < today) {
                overdueItems.add(assignment);
                continue;
            }
            if (nearestUpcoming == null || assignment.dueMillis < nearestUpcoming.dueMillis) {
                nearestUpcoming = assignment;
            }
        }
        Collections.sort(overdueItems, (a, b) -> Long.compare(a.dueMillis, b.dueMillis));

        if (nearestUpcoming == null) {
            nearestText.setText("다가오는 수행평가가 없어요");
            nearestMetaText.setText(overdueItems.isEmpty()
                ? "등록 버튼으로 첫 일정을 추가해 보세요."
                : "기한 지난 과제를 먼저 확인해 주세요.");
            nearestCard.setOnClickListener(null);
            nearestCard.setContentDescription("다가오는 수행평가 없음");
        } else {
            Assignment target = nearestUpcoming;
            nearestText.setText(target.title);
            String dueLabel = sameDay(target.dueMillis, today) ? "오늘 마감" : shortDateFormat.format(target.dueMillis);
            nearestMetaText.setText(dueLabel + " · " + target.subject + " · " + target.period + " · " + dDayText(target.dueMillis));
            nearestCard.setOnClickListener(view -> showDetail(target));
            nearestCard.setContentDescription("다가오는 수행평가, " + target.title + ", " + dueLabel + ", 상세 보기");
        }

        if (overdueItems.isEmpty()) {
            overdueCard.setVisibility(View.GONE);
            overdueCard.setOnClickListener(null);
        } else {
            overdueCard.setVisibility(View.VISIBLE);
            overdueText.setText("기한 지난 과제 " + overdueItems.size() + "개");
            overdueMetaText.setText("자동 완료하지 않았어요. 눌러서 확인해요.");
            ArrayList<Assignment> overdueCopy = new ArrayList<>(overdueItems);
            overdueCard.setContentDescription("기한 지난 과제 " + overdueItems.size() + "개, 목록 보기");
            overdueCard.setOnClickListener(view -> showAssignmentListDialog(
                "기한 지난 과제",
                overdueCopy.size() + "개의 미완료 과제가 지나갔어요.",
                overdueCopy
            ));
        }
    }

    private void refreshHeaderSummary() {
        Calendar weekStart = startOfToday();
        weekStart.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY);
        Calendar weekEnd = (Calendar) weekStart.clone();
        weekEnd.add(Calendar.DAY_OF_MONTH, 6);
        long today = startOfToday().getTimeInMillis();
        int weekCount = 0;
        int upcomingCount = 0;
        for (Assignment assignment : assignments) {
            if (assignment.complete) {
                continue;
            }
            if (assignment.dueMillis >= weekStart.getTimeInMillis() && assignment.dueMillis <= weekEnd.getTimeInMillis()) {
                weekCount++;
            }
            if (assignment.dueMillis >= today) {
                upcomingCount++;
            }
        }
        brandSubtitle.setText("이번 주 " + weekCount + "개 · 예정 " + upcomingCount + "개");
    }

    private void refreshWeek() {
        weekList.removeAllViews();
        Calendar start = startOfToday();
        start.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY);
        Calendar end = (Calendar) start.clone();
        end.add(Calendar.DAY_OF_MONTH, 6);

        TextView range = text(shortDateFormat.format(start.getTime()) + " ~ " + shortDateFormat.format(end.getTime()), 13, color(R.color.text_primary), Typeface.BOLD);
        range.setBackground(pill(color(R.color.lavender), 0));
        range.setPadding(dp(10), dp(5), dp(10), dp(5));
        weekList.addView(range, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        List<Assignment> weekItems = new ArrayList<>();
        for (Assignment assignment : assignments) {
            if (assignment.dueMillis >= start.getTimeInMillis() && assignment.dueMillis <= end.getTimeInMillis()) {
                weekItems.add(assignment);
            }
        }
        Collections.sort(weekItems, (a, b) -> Long.compare(a.dueMillis, b.dueMillis));
        resizeWeekList(weekItems.size());

        if (weekItems.isEmpty()) {
            TextView empty = text("이번 주에 수행평가가 없습니다.", 14, color(R.color.text_secondary), Typeface.NORMAL);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(12), dp(18), dp(12), dp(18));
            empty.setBackground(roundRect(color(R.color.surface_primary), 0, dp(18)));
            LinearLayout.LayoutParams emptyParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            );
            emptyParams.topMargin = dp(12);
            weekList.addView(empty, emptyParams);
            return;
        }

        boolean compactRows = weekItems.size() > 2;
        for (int index = 0; index < weekItems.size(); index++) {
            View row = assignmentRow(weekItems.get(index), compactRows);
            if (index == 0) {
                LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) row.getLayoutParams();
                params.topMargin = dp(12);
                row.setLayoutParams(params);
            }
            weekList.addView(row);
        }
    }

    private void resizeWeekList(int itemCount) {
        if (weekListScroll == null) {
            return;
        }
        ViewGroup.LayoutParams params = weekListScroll.getLayoutParams();
        int visibleItems = Math.min(Math.max(itemCount, 1), 2);
        params.height = dp(itemCount > 2 ? 292 : 64 + (visibleItems * 96));
        weekListScroll.setLayoutParams(params);
    }

    private void refreshCalendar() {
        calendarGrid.removeAllViews();
        monthTitle.setText(String.format(Locale.KOREAN, "%d년 %d월", visibleMonth.get(Calendar.YEAR), visibleMonth.get(Calendar.MONTH) + 1));

        String[] weekdays = {"일", "월", "화", "수", "목", "금", "토"};
        for (int index = 0; index < weekdays.length; index++) {
            int weekdayColor = color(R.color.text_secondary);
            if (index == 0) {
                weekdayColor = color(R.color.calendar_holiday);
            } else if (index == 6) {
                weekdayColor = color(R.color.calendar_saturday);
            }
            TextView cell = text(weekdays[index], 12, weekdayColor, Typeface.BOLD);
            cell.setGravity(Gravity.CENTER);
            cell.setPadding(0, 0, 0, dp(8));
            calendarGrid.addView(cell, cellParams());
        }

        Calendar cursor = (Calendar) visibleMonth.clone();
        int firstDayOffset = cursor.get(Calendar.DAY_OF_WEEK) - 1;
        cursor.add(Calendar.DAY_OF_MONTH, -firstDayOffset);

        for (int i = 0; i < 42; i++) {
            calendarGrid.addView(dayCell(cursor), cellParams());
            cursor.add(Calendar.DAY_OF_MONTH, 1);
        }
    }

    private View dayCell(Calendar day) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        cell.setPadding(dp(2), dp(6), dp(2), dp(5));
        cell.setMinimumHeight(dp(70));

        boolean inVisibleMonth = day.get(Calendar.MONTH) == visibleMonth.get(Calendar.MONTH);
        HolidayInfo holiday = holidayInfo(day);
        boolean isSunday = day.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY;
        boolean isSaturday = day.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY;
        int textColor = calendarDateColor(inVisibleMonth, isSunday, isSaturday, holiday != null);
        TextView number = text(String.valueOf(day.get(Calendar.DAY_OF_MONTH)), 12, textColor, Typeface.BOLD);
        number.setGravity(Gravity.CENTER);
        boolean isSelected = sameDay(day.getTimeInMillis(), selectedCalendarDay.getTimeInMillis());
        boolean isToday = sameDay(day.getTimeInMillis(), startOfToday().getTimeInMillis());
        if (isSelected) {
            number.setBackground(circle(color(R.color.lavender_strong)));
        } else if (isToday) {
            number.setBackground(circle(color(R.color.butter_yellow)));
        } else if (holiday != null && inVisibleMonth) {
            number.setBackground(circle(color(R.color.holiday_soft)));
        }
        cell.addView(number, new LinearLayout.LayoutParams(dp(32), dp(32)));

        if (holiday != null && inVisibleMonth) {
            TextView holidayLabel = text(holiday.shortName, 9, color(R.color.calendar_holiday), Typeface.BOLD);
            holidayLabel.setGravity(Gravity.CENTER);
            holidayLabel.setMaxLines(1);
            holidayLabel.setPadding(dp(3), dp(1), dp(3), dp(1));
            LinearLayout.LayoutParams holidayParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            );
            holidayParams.topMargin = dp(3);
            cell.addView(holidayLabel, holidayParams);
        }

        List<Assignment> dayAssignments = new ArrayList<>();
        for (Assignment assignment : assignments) {
            if (sameDay(day.getTimeInMillis(), assignment.dueMillis)) {
                dayAssignments.add(assignment);
            }
        }
        if (!dayAssignments.isEmpty()) {
            View dot = new View(this);
            boolean allComplete = true;
            for (Assignment assignment : dayAssignments) {
                if (!assignment.complete) {
                    allComplete = false;
                    break;
                }
            }
            dot.setBackground(circle(color(allComplete ? R.color.completed : R.color.charcoal)));
            LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dp(6), dp(6));
            dotParams.topMargin = holiday != null && inVisibleMonth ? dp(3) : dp(5);
            cell.addView(dot, dotParams);
        }
        Calendar clickedDay = (Calendar) day.clone();
        cell.setOnClickListener(view -> {
            selectedCalendarDay = clickedDay;
            refreshCalendar();
            if (dayAssignments.size() == 1) {
                showDetail(dayAssignments.get(0));
            } else if (dayAssignments.size() > 1) {
                showAssignmentsForDay(dayAssignments);
            }
        });
        return cell;
    }

    private View assignmentRow(Assignment assignment) {
        return assignmentRow(assignment, false);
    }

    private View assignmentRow(Assignment assignment, boolean compact) {
        LinearLayout row = surfaceCard(assignment.complete ? color(R.color.completed) : color(R.color.surface_primary), color(R.color.outline));
        if (compact) {
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(14), dp(10), dp(12), dp(10));
            row.setMinimumHeight(dp(58));
            LinearLayout.LayoutParams compactParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            );
            compactParams.bottomMargin = dp(8);
            row.setLayoutParams(compactParams);
            row.setOnClickListener(view -> showDetail(assignment));

            LinearLayout copy = new LinearLayout(this);
            copy.setOrientation(LinearLayout.VERTICAL);

            TextView title = text(assignment.title, 14, color(R.color.text_primary), Typeface.BOLD);
            title.setSingleLine(true);
            title.setEllipsize(TextUtils.TruncateAt.END);
            copy.addView(title);

            String meta = assignment.subject + " · " + shortDateFormat.format(assignment.dueMillis) + " · " + assignment.period + (assignment.complete ? " · 완료" : "");
            TextView metaText = text(meta, 11, color(R.color.text_secondary), Typeface.NORMAL);
            metaText.setSingleLine(true);
            metaText.setEllipsize(TextUtils.TruncateAt.END);
            metaText.setPadding(0, dp(4), dp(8), 0);
            copy.addView(metaText);

            row.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

            TextView dday = text(dDayText(assignment.dueMillis), 11, color(R.color.text_primary), Typeface.BOLD);
            dday.setPadding(dp(9), dp(4), dp(9), dp(4));
            dday.setBackground(pill(color(R.color.lavender), 0));
            row.addView(dday);
            return row;
        }

        row.setPadding(dp(18), dp(15), dp(18), dp(15));
        row.setOnClickListener(view -> showDetail(assignment));

        LinearLayout top = row();
        TextView subject = text(assignment.subject, 12, color(R.color.text_secondary), Typeface.BOLD);
        top.addView(subject, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView dday = text(dDayText(assignment.dueMillis), 12, color(R.color.text_primary), Typeface.BOLD);
        dday.setPadding(dp(10), dp(5), dp(10), dp(5));
        dday.setBackground(pill(color(R.color.lavender), 0));
        top.addView(dday);
        row.addView(top);

        TextView title = text(assignment.title, 16, color(R.color.text_primary), Typeface.BOLD);
        title.setPadding(0, dp(5), 0, dp(5));
        row.addView(title);

        String meta = shortDateFormat.format(assignment.dueMillis) + " · " + assignment.period + (assignment.complete ? " · 완료" : "");
        row.addView(text(meta, 13, color(R.color.text_secondary), Typeface.NORMAL));
        return row;
    }

    private void showAssignmentsForDay(List<Assignment> dayAssignments) {
        showAssignmentListDialog("이 날짜의 수행평가", dayAssignments.size() + "개의 일정이 있어요", dayAssignments);
    }

    private void showAssignmentListDialog(String titleValue, String subtitleValue, List<Assignment> items) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(22), dp(22), dp(22), dp(18));
        card.setBackground(roundRect(color(R.color.surface_primary), 0, dp(28)));

        LinearLayout head = row();
        head.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(text(titleValue, 21, color(R.color.text_primary), Typeface.BOLD));
        TextView count = text(subtitleValue, 13, color(R.color.text_secondary), Typeface.NORMAL);
        count.setPadding(0, dp(6), 0, 0);
        copy.addView(count);
        head.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        FrameLayout close = closeButtonTarget("안내창 닫기", color(R.color.surface_soft), color(R.color.text_primary));
        close.setOnClickListener(view -> dialog.dismiss());
        head.addView(close, new LinearLayout.LayoutParams(dp(48), dp(48)));
        card.addView(head);

        ScrollView listScroll = new MaxHeightScrollView(this, (int) (getResources().getDisplayMetrics().heightPixels * 0.52f));
        listScroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        listScroll.addView(list, new ScrollView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        listParams.topMargin = dp(18);
        card.addView(listScroll, listParams);

        for (Assignment assignment : items) {
            list.addView(dayDialogRow(assignment, dialog));
        }

        dialog.setContentView(card);
        dialog.show();
        styleCenteredDialog(dialog, 0.92f, 420);
    }

    private View dayDialogRow(Assignment assignment, Dialog parentDialog) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(13), dp(14), dp(13));
        row.setBackground(roundRect(assignment.complete ? color(R.color.completed) : color(R.color.surface_soft), 0, dp(20)));
        row.setClickable(true);
        row.setFocusable(true);
        row.setContentDescription(assignment.title + ", " + assignment.period + ", 상세 보기");

        ImageView icon = new ImageView(this);
        icon.setImageDrawable(tintedDrawable(R.drawable.ic_assignment, color(R.color.text_primary)));
        icon.setPadding(dp(9), dp(9), dp(9), dp(9));
        icon.setBackground(circle(color(R.color.surface_primary)));
        row.addView(icon, new LinearLayout.LayoutParams(dp(42), dp(42)));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        TextView title = text(assignment.title, 16, color(R.color.text_primary), Typeface.BOLD);
        title.setSingleLine(false);
        copy.addView(title);
        String metaText = assignment.subject + " · " + assignment.period + (assignment.complete ? " · 완료" : "");
        TextView meta = text(metaText, 13, color(R.color.text_secondary), Typeface.NORMAL);
        meta.setPadding(0, dp(5), 0, 0);
        copy.addView(meta);
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        copyParams.leftMargin = dp(12);
        row.addView(copy, copyParams);

        TextView dday = text(dDayText(assignment.dueMillis), 12, color(R.color.text_primary), Typeface.BOLD);
        dday.setPadding(dp(10), dp(5), dp(10), dp(5));
        dday.setBackground(pill(color(R.color.lavender), 0));
        row.addView(dday);

        row.setOnClickListener(view -> {
            parentDialog.dismiss();
            showDetail(assignment);
        });

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.bottomMargin = dp(10);
        row.setLayoutParams(params);
        return row;
    }

    private void showDetail(Assignment assignment) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(22), dp(22), dp(22), dp(22));
        card.setBackground(roundRect(color(R.color.surface_primary), 0, dp(28)));

        LinearLayout head = row();
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text(assignment.title, 22, color(R.color.text_primary), Typeface.BOLD);
        head.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        FrameLayout close = closeButtonTarget("상세 정보 닫기", color(R.color.surface_soft), color(R.color.text_primary));
        close.setOnClickListener(view -> dialog.dismiss());
        head.addView(close, new LinearLayout.LayoutParams(dp(48), dp(48)));
        card.addView(head);

        ScrollView detailScroll = new MaxHeightScrollView(this, (int) (getResources().getDisplayMetrics().heightPixels * 0.48f));
        detailScroll.setFillViewport(false);
        detailScroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        detailScroll.setClipToPadding(false);
        LinearLayout detailContent = new LinearLayout(this);
        detailContent.setOrientation(LinearLayout.VERTICAL);
        detailScroll.addView(detailContent, new ScrollView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView meta = text(assignment.subject + " · " + assignment.period + " · " + dateFormat.format(assignment.dueMillis), 13, color(R.color.text_secondary), Typeface.NORMAL);
        meta.setPadding(0, dp(10), 0, 0);
        detailContent.addView(meta);

        TextView dday = text(dDayText(assignment.dueMillis), 13, color(R.color.text_primary), Typeface.BOLD);
        dday.setPadding(dp(12), dp(6), dp(12), dp(6));
        dday.setBackground(pill(assignment.complete ? color(R.color.completed) : color(R.color.butter_yellow), 0));
        LinearLayout.LayoutParams ddayParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ddayParams.topMargin = dp(14);
        detailContent.addView(dday, ddayParams);

        if (!assignment.detail.isEmpty()) {
            TextView detailLabel = eyebrow("세부 내용");
            detailLabel.setPadding(0, dp(20), 0, dp(8));
            detailContent.addView(detailLabel);
            TextView detail = text(assignment.detail, 15, color(R.color.text_primary), Typeface.NORMAL);
            detail.setPadding(dp(16), dp(14), dp(16), dp(14));
            detail.setBackground(roundRect(color(R.color.surface_soft), 0, dp(18)));
            detailContent.addView(detail);
        }

        if (!assignment.photos.isEmpty()) {
            TextView photoLabel = eyebrow("첨부사진");
            photoLabel.setPadding(0, dp(20), 0, dp(8));
            detailContent.addView(photoLabel);
            HorizontalScrollView scroll = new HorizontalScrollView(this);
            scroll.setHorizontalScrollBarEnabled(false);
            LinearLayout photos = row();
            for (String photo : assignment.photos) {
                ImageView image = new ImageView(this);
                image.setImageURI(Uri.parse(photo));
                image.setScaleType(ImageView.ScaleType.CENTER_CROP);
                image.setContentDescription("첨부사진 크게 보기");
                image.setBackground(roundRect(color(R.color.surface_soft), 0, dp(16)));
                image.setClipToOutline(true);
                image.setOnClickListener(view -> openPhoto(Uri.parse(photo)));
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(92), dp(92));
                params.rightMargin = dp(8);
                photos.addView(image, params);
            }
            scroll.addView(photos);
            detailContent.addView(scroll);
        }

        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        scrollParams.topMargin = dp(6);
        card.addView(detailScroll, scrollParams);

        LinearLayout actions = row();
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionsParams.topMargin = dp(22);
        Button delete = actionButton("삭제", color(R.color.danger_soft), color(R.color.danger));
        delete.setOnClickListener(view -> confirmDelete(assignment, dialog));
        actions.addView(delete, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Button edit = actionButton("수정", color(R.color.lavender_soft), color(R.color.text_primary));
        edit.setOnClickListener(view -> {
            dialog.dismiss();
            editAssignment(assignment);
        });
        LinearLayout.LayoutParams editParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        editParams.leftMargin = dp(8);
        actions.addView(edit, editParams);
        Button complete = actionButton(assignment.complete ? "미완료로 변경" : "완료로 변경", color(R.color.charcoal), Color.WHITE);
        complete.setTextSize(assignment.complete ? 12 : 13);
        complete.setOnClickListener(view -> {
            if (!canManagePublication(assignment)) return;
            detachUnpublished(assignment);
            assignment.complete = !assignment.complete;
            if (!savePublication(assignment, false)) {
                loadAssignments();
                Toast.makeText(this, "저장하지 못했어요", Toast.LENGTH_SHORT).show();
            }
            refreshUi();
            dialog.dismiss();
        });
        LinearLayout.LayoutParams completeParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        completeParams.leftMargin = dp(8);
        actions.addView(complete, completeParams);
        if (!isLocalOnly(assignment) && !ownsPublication(assignment)) {
            edit.setEnabled(false);
            complete.setEnabled(false);
            delete.setEnabled(false);
        }
        card.addView(text(assignment.isPublic ? "공개 과제 · 작성자만 수정할 수 있어요" : "비공개 과제 · 내 휴대폰에서만 보여요",
            13, color(R.color.text_secondary), Typeface.NORMAL));
        if (!assignment.cloudId.isEmpty() && AssignmentSync.get(this).hasPending(assignment.cloudId))
            card.addView(text("변경 대기 중 · 다른 사람에게 이전 내용이 보일 수 있어요", 13, color(R.color.text_secondary), Typeface.NORMAL));
        card.addView(actions, actionsParams);

        dialog.setContentView(card);
        dialog.show();
        styleCenteredDialog(dialog, 0.92f, 440);
    }

    private void confirmDelete(Assignment assignment, Dialog parentDialog) {
        Dialog confirm = new Dialog(this);
        confirm.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(22), dp(22), dp(22), dp(22));
        card.setBackground(roundRect(color(R.color.surface_primary), 0, dp(26)));
        card.addView(text("정말로 삭제하시겠습니까?", 20, color(R.color.text_primary), Typeface.BOLD));
        TextView message = text("삭제하면 되돌릴 수 없어요.", 14, color(R.color.text_secondary), Typeface.NORMAL);
        message.setPadding(0, dp(10), 0, dp(20));
        card.addView(message);
        LinearLayout actions = row();
        Button cancel = actionButton("취소", color(R.color.surface_soft), color(R.color.text_primary));
        cancel.setOnClickListener(view -> confirm.dismiss());
        actions.addView(cancel, new LinearLayout.LayoutParams(0, dp(52), 1));
        Button delete = actionButton("삭제", color(R.color.danger), Color.WHITE);
        delete.setOnClickListener(view -> {
            if (!canManagePublication(assignment)) return;
            detachUnpublished(assignment);
            assignments.remove(assignment);
            if (!savePublication(assignment, true)) {
                loadAssignments();
                Toast.makeText(this, "삭제하지 못했어요", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!assignment.cloudId.isEmpty()) Toast.makeText(this, "공개 과제 삭제가 아직 끝나지 않았어요. ‘공개 범위’ 아래에서 확인해 주세요", Toast.LENGTH_LONG).show();
            refreshUi();
            confirm.dismiss();
            parentDialog.dismiss();
        });
        LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(0, dp(52), 1);
        deleteParams.leftMargin = dp(8);
        actions.addView(delete, deleteParams);
        card.addView(actions);
        confirm.setContentView(card);
        confirm.show();
        styleCenteredDialog(confirm, 0.88f, 400);
    }

    private void editAssignment(Assignment assignment) {
        if (!canManagePublication(assignment)) return;
        Runnable loadEditForm = () -> loadAssignmentIntoForm(assignment);
        if (hasDraftChanges() && (editingAssignmentId == null || !editingAssignmentId.equals(assignment.id))) {
            showDiscardDraftDialog(loadEditForm);
            return;
        }
        loadEditForm.run();
    }

    private void loadAssignmentIntoForm(Assignment assignment) {
        editingAssignment = assignment;
        editingAssignmentId = assignment.id;
        visibilitySpinner.setSelection(assignment.isPublic ? 1 : 0);
        titleInput.setText(assignment.title);
        detailInput.setText(assignment.detail);
        selectedPhotos.clear();
        for (String photo : assignment.photos) {
            selectedPhotos.add(Uri.parse(photo));
        }
        refreshPhotoStatusText();
        refreshPhotoPreview();
        selectedDueDate.setTimeInMillis(assignment.dueMillis);
        dueDateButton.setText(dateFormat.format(selectedDueDate.getTime()));
        setSpinnerValue(periodSpinner, assignment.period);
        if (!setSpinnerValue(subjectSpinner, assignment.subject)) {
            setSpinnerValue(subjectSpinner, "직접 입력");
            customSubjectInput.setText(assignment.subject);
            customSubjectInput.setVisibility(View.VISIBLE);
        }
        if (formBody != null) {
            formBody.setVisibility(View.VISIBLE);
            formToggleButton.setText("접기");
        }
        updateFormMode();
        scrollTo(formSection);
        titleInput.requestFocus();
    }

    private void showNavigationMenu() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(true);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(24), dp(28), dp(24), dp(28));
        panel.setBackground(sidePanelBackground());

        LinearLayout head = row();
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("메뉴", 24, color(R.color.text_primary), Typeface.BOLD);
        head.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        FrameLayout close = closeButtonTarget("메뉴 닫기", color(R.color.surface_soft), color(R.color.text_primary));
        close.setOnClickListener(view -> dialog.dismiss());
        head.addView(close, new LinearLayout.LayoutParams(dp(48), dp(48)));
        panel.addView(head);

        TextView mainLabel = eyebrow("주요 화면");
        mainLabel.setPadding(0, dp(28), 0, dp(8));
        panel.addView(mainLabel);
        panel.addView(sideMenuButton(R.drawable.ic_week, "이번 주 수행평가", dialog, () -> scrollTo(weekSection)));
        panel.addView(sideMenuButton(R.drawable.ic_calendar, "캘린더 보기", dialog, () -> scrollTo(calendarSection)));

        TextView settingLabel = eyebrow("설정");
        settingLabel.setPadding(0, dp(24), 0, dp(8));
        panel.addView(settingLabel);
        panel.addView(sideMenuButton(R.drawable.ic_assignment, "수행평가 등록", dialog, this::openFormAndScroll));
        panel.addView(sideMenuButton(R.drawable.ic_notifications, "알림 설정", dialog, this::openReminderAndScroll));
        panel.addView(sideMenuButton(R.drawable.ic_assignment, "공개 과제 가져오기", dialog, this::fetchAssignmentsFromFirestore));


        FirebaseUser account = firebaseAuth.getCurrentUser();
        boolean signedIn = account != null && !account.isAnonymous();
        panel.addView(sideMenuButton(R.drawable.ic_assignment, signedIn ? "마이페이지" : "로그인", dialog,
            () -> startActivity(new android.content.Intent(this, signedIn ? MyPageActivity.class : AuthActivity.class))));

        dialog.setContentView(panel);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setGravity(Gravity.END);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams attributes = window.getAttributes();
            attributes.width = Math.min((int) (getResources().getDisplayMetrics().widthPixels * 0.86f), dp(360));
            attributes.height = WindowManager.LayoutParams.MATCH_PARENT;
            attributes.dimAmount = 0.42f;
            attributes.windowAnimations = R.style.SidePanelAnimation;
            window.setAttributes(attributes);
        }
        dialog.show();

        if (window != null) {
            window.setLayout(
                Math.min((int) (getResources().getDisplayMetrics().widthPixels * 0.86f), dp(360)),
                WindowManager.LayoutParams.MATCH_PARENT
            );
        }
    }

    private Button sideMenuButton(int iconRes, String value, Dialog dialog, Runnable action) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextColor(color(R.color.text_primary));
        button.setTextSize(15);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        button.setPadding(dp(18), 0, dp(18), 0);
        button.setCompoundDrawablesWithIntrinsicBounds(tintedDrawable(iconRes, color(R.color.text_primary)), null, null, null);
        button.setCompoundDrawablePadding(dp(12));
        button.setBackground(pill(color(R.color.surface_soft), 0));
        button.setOnClickListener(view -> {
            dialog.dismiss();
            rootScrollView.postDelayed(action, 180);
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(52)
        );
        params.bottomMargin = dp(8);
        button.setLayoutParams(params);
        return button;
    }

    private void openFormAndScroll() {
        Runnable openNewForm = () -> {
            editingAssignment = null;
            editingAssignmentId = null;
            clearForm();
            updateFormMode();
            showFormBody();
            scrollTo(formSection);
        };
        if (hasDraftChanges()) {
            showDiscardDraftDialog(openNewForm);
            return;
        }
        openNewForm.run();
    }

    private void showFormBody() {
        if (formBody != null) {
            formBody.setVisibility(View.VISIBLE);
            formToggleButton.setText("접기");
        }
    }

    private void openReminderAndScroll() {
        if (reminderBody != null) {
            reminderBody.setVisibility(View.VISIBLE);
            reminderToggleButton.setText("접기");
        }
        scrollTo(reminderSection);
    }

    private void scrollTo(View target) {
        if (target == null || rootScrollView == null) {
            return;
        }
        rootScrollView.post(() -> rootScrollView.smoothScrollTo(0, Math.max(0, target.getTop() - dp(12))));
    }

    private void openPhoto(Uri uri) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "image/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(intent);
        } catch (Exception exception) {
            Toast.makeText(this, "사진을 열 수 없습니다", Toast.LENGTH_SHORT).show();
        }
    }

    private void clearForm() {
        titleInput.setText("");
        detailInput.setText("");
        customSubjectInput.setText("");
        customSubjectInput.setVisibility(View.GONE);
        subjectSpinner.setSelection(0);
        periodSpinner.setSelection(0);
        visibilitySpinner.setSelection(0);
        subjectErrorText.setVisibility(View.GONE);
        periodErrorText.setVisibility(View.GONE);
        selectedPhotos.clear();
        selectedDueDate = startOfToday();
        refreshPhotoStatusText();
        refreshPhotoPreview();
    }

    private void cancelFormEditing() {
        Runnable cancel = () -> {
            editingAssignment = null;
            editingAssignmentId = null;
            clearForm();
            updateFormMode();
            Toast.makeText(this, "수정을 취소했어요", Toast.LENGTH_SHORT).show();
        };
        if (hasDraftChanges()) {
            showDiscardDraftDialog(cancel);
        } else {
            cancel.run();
        }
    }

    private void updateFormMode() {
        boolean editing = editingAssignmentId != null;
        if (formHeading != null) {
            formHeading.setText(editing ? "수행평가 수정" : "수행평가 등록");
        }
        if (formSaveButton != null) {
            formSaveButton.setText(editing ? "수정 저장" : "등록하기");
        }
        if (formCancelButton != null) {
            formCancelButton.setVisibility(editing ? View.VISIBLE : View.GONE);
        }
    }

    private boolean hasDraftChanges() {
        if (titleInput == null || detailInput == null || subjectSpinner == null || periodSpinner == null) {
            return false;
        }
        if (editingAssignmentId != null) {
            Assignment original = findAssignmentById(editingAssignmentId);
            if (original == null) {
                return true;
            }
            return !original.title.equals(titleInput.getText().toString().trim())
                || original.isPublic != (visibilitySpinner.getSelectedItemPosition() == 1)
                || !original.detail.equals(detailInput.getText().toString().trim())
                || !original.subject.equals(currentSubjectFromForm())
                || !original.period.equals(periodSpinner.getSelectedItem().toString())
                || original.dueMillis != selectedDueDate.getTimeInMillis()
                || !samePhotos(original.photos, selectedPhotos);
        }
        boolean dueDateChanged = selectedDueDate != null && !sameDay(selectedDueDate.getTimeInMillis(), startOfToday().getTimeInMillis());
        return titleInput.getText().toString().trim().length() > 0
            || visibilitySpinner.getSelectedItemPosition() != 0
            || detailInput.getText().toString().trim().length() > 0
            || customSubjectInput.getText().toString().trim().length() > 0
            || subjectSpinner.getSelectedItemPosition() != 0
            || periodSpinner.getSelectedItemPosition() != 0
            || !selectedPhotos.isEmpty()
            || dueDateChanged;
    }

    private String currentSubjectFromForm() {
        String subject = subjectSpinner.getSelectedItem().toString();
        return "직접 입력".equals(subject) ? customSubjectInput.getText().toString().trim() : subject;
    }

    private boolean samePhotos(List<String> originalPhotos, List<Uri> currentPhotos) {
        if (originalPhotos.size() != currentPhotos.size()) {
            return false;
        }
        for (int i = 0; i < originalPhotos.size(); i++) {
            if (!originalPhotos.get(i).equals(currentPhotos.get(i).toString())) {
                return false;
            }
        }
        return true;
    }

    private void showDiscardDraftDialog(Runnable onDiscard) {
        Dialog confirm = new Dialog(this);
        confirm.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(22), dp(22), dp(22), dp(22));
        card.setBackground(roundRect(color(R.color.surface_primary), 0, dp(26)));
        card.addView(text("작성 중인 내용을 버릴까요?", 20, color(R.color.text_primary), Typeface.BOLD));
        TextView message = text("지금 작성한 내용은 사라져요.", 14, color(R.color.text_secondary), Typeface.NORMAL);
        message.setPadding(0, dp(10), 0, dp(20));
        card.addView(message);
        LinearLayout actions = row();
        Button keep = actionButton("계속 작성", color(R.color.surface_soft), color(R.color.text_primary));
        keep.setOnClickListener(view -> confirm.dismiss());
        actions.addView(keep, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Button discard = actionButton("버리기", color(R.color.danger), Color.WHITE);
        discard.setOnClickListener(view -> {
            confirm.dismiss();
            onDiscard.run();
        });
        LinearLayout.LayoutParams discardParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        discardParams.leftMargin = dp(8);
        actions.addView(discard, discardParams);
        card.addView(actions);
        confirm.setContentView(card);
        confirm.show();
        styleCenteredDialog(confirm, 0.88f, 400);
    }

    private void restoreTransientState(Bundle savedInstanceState) {
        visibilitySpinner.setSelection(savedInstanceState.getInt("state_visibility", 0) == 1 ? 1 : 0);
        titleInput.setText(savedInstanceState.getString(STATE_TITLE, ""));
        detailInput.setText(savedInstanceState.getString(STATE_DETAIL, ""));
        customSubjectInput.setText(savedInstanceState.getString(STATE_CUSTOM_SUBJECT, ""));
        subjectSpinner.setSelection(clampSpinnerIndex(subjectSpinner, savedInstanceState.getInt(STATE_SUBJECT_INDEX, 0)));
        periodSpinner.setSelection(clampSpinnerIndex(periodSpinner, savedInstanceState.getInt(STATE_PERIOD_INDEX, 0)));
        selectedDueDate.setTimeInMillis(savedInstanceState.getLong(STATE_DUE_MILLIS, startOfToday().getTimeInMillis()));
        dueDateButton.setText(dateFormat.format(selectedDueDate.getTime()));

        selectedPhotos.clear();
        ArrayList<String> photoStrings = savedInstanceState.getStringArrayList(STATE_PHOTOS);
        if (photoStrings != null) {
            for (String photo : photoStrings) {
                selectedPhotos.add(Uri.parse(photo));
            }
        }
        refreshPhotoStatusText();
        refreshPhotoPreview();

        editingAssignmentId = savedInstanceState.getString(STATE_EDITING_ID);
        editingAssignment = editingAssignmentId == null ? null : findAssignmentById(editingAssignmentId);
        if (editingAssignmentId != null && editingAssignment == null) {
            editingAssignmentId = null;
        }

        visibleMonth.setTimeInMillis(savedInstanceState.getLong(STATE_VISIBLE_MONTH, visibleMonth.getTimeInMillis()));
        selectedCalendarDay.setTimeInMillis(savedInstanceState.getLong(STATE_SELECTED_DAY, selectedCalendarDay.getTimeInMillis()));
        reminderDraftDays = savedInstanceState.getInt(STATE_REMINDER_DRAFT_DAYS, prefs.getInt(KEY_REMINDER_DAYS, 0));
        reminderDraftTime = savedInstanceState.getString(STATE_REMINDER_DRAFT_TIME, prefs.getString(KEY_REMINDER_TIME, null));
        reminderDaySpinner.setSelection(clampSpinnerIndex(reminderDaySpinner, reminderDraftDays));
        updateReminderTimeButton();
        updateFormMode();

        if (savedInstanceState.getBoolean(STATE_FORM_VISIBLE, false)) {
            showFormBody();
        }
        if (savedInstanceState.getBoolean(STATE_REMINDER_VISIBLE, false) && reminderBody != null) {
            reminderBody.setVisibility(View.VISIBLE);
            reminderToggleButton.setText("접기");
        }
    }

    private int clampSpinnerIndex(Spinner spinner, int index) {
        if (spinner == null || spinner.getCount() == 0) {
            return 0;
        }
        return Math.max(0, Math.min(index, spinner.getCount() - 1));
    }

    private Assignment findAssignmentById(String id) {
        if (id == null) {
            return null;
        }
        for (Assignment assignment : assignments) {
            if (id.equals(assignment.id)) {
                return assignment;
            }
        }
        return null;
    }

    private void loadReminderDraftFromSavedSettings() {
        reminderDraftDays = prefs.getInt(KEY_REMINDER_DAYS, 0);
        reminderDraftTime = prefs.getString(KEY_REMINDER_TIME, null);
        if (reminderDaySpinner != null) {
            reminderDaySpinner.setSelection(clampSpinnerIndex(reminderDaySpinner, reminderDraftDays));
        }
        updateReminderTimeButton();
    }

    private void updateReminderTimeButton() {
        if (reminderTimeButton != null) {
            reminderTimeButton.setText(reminderDraftTime == null || reminderDraftTime.isEmpty() ? "알림 시간 선택" : reminderDraftTime);
        }
    }

    private void refreshReminderSummary() {
        if (reminderSummary == null) return;
        if (!prefs.getBoolean(ReminderScheduler.ENABLED, false)) {
            reminderSummary.setText("알림이 꺼져 있어요. 날짜와 시간을 선택하고 ‘알림 켜기·저장’을 눌러 주세요.");
            return;
        }
        String day = reminderDaySpinner.getItemAtPosition(clampSpinnerIndex(reminderDaySpinner, prefs.getInt(KEY_REMINDER_DAYS, 0))).toString();
        String time = prefs.getString(KEY_REMINDER_TIME, "시간 미선택");
        if (!ReminderScheduler.allowed(this)) {
            if (reminderSystemSettings != null && reminderMoreButton != null) {
                reminderSystemSettings.setVisibility(View.VISIBLE);
                reminderMoreButton.setText("추가 설정 접기");
                reminderMoreButton.setContentDescription("휴대폰 알림 권한과 정확한 시간 설정 접기");
            }
            reminderSummary.setText(day + " · " + time + " 저장됨\n휴대폰에서 알림이 차단돼 있어요. ‘휴대폰 알림 설정 열기’에서 허용해 주세요.");
            return;
        }
        long next = prefs.getLong(ReminderScheduler.PENDING, -1);
        reminderSummary.setText(day + " · " + time + " 알림 켜짐\n"
            + (next > 0 ? "다음 알림: " + new SimpleDateFormat("M월 d일 HH:mm", Locale.KOREAN).format(new java.util.Date(next))
                : "지금은 알림을 보낼 과제가 없어요.")
            + (ReminderScheduler.exact(this) ? "" : "\n절전 상태에서는 늦게 올 수 있어요. ‘추가 설정’에서 ‘정확한 시간 알림 허용’을 눌러 주세요."));
    }

    private void requestReminderPermission() {
        if (android.os.Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 8205);
        } else if (!ReminderScheduler.allowed(this)) openNotificationSettings();
    }

    private void openNotificationSettings() {
        Intent settings = android.os.Build.VERSION.SDK_INT >= 26
            ? new Intent(android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, getPackageName())
                .putExtra(android.provider.Settings.EXTRA_CHANNEL_ID, ReminderScheduler.CHANNEL)
            : new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()));
        try { startActivity(settings); }
        catch (android.content.ActivityNotFoundException e) {
            Toast.makeText(this, "휴대폰 설정에서 앱 알림을 허용해 주세요", Toast.LENGTH_LONG).show();
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == 8205) {
            ReminderScheduler.reschedule(this);
            refreshReminderSummary();
            if (!ReminderScheduler.allowed(this)) Toast.makeText(this, "알림을 받으려면 휴대폰 설정에서 알림을 허용해 주세요", Toast.LENGTH_LONG).show();
        }
    }

    private void fetchAssignmentsFromFirestore() {
        if (firestoreDb == null || firebaseAuth == null) {
            Toast.makeText(this, "아직 연결을 준비 중이에요. 잠시 후 다시 시도해 주세요", Toast.LENGTH_SHORT).show();
            return;
        }
        FirebaseUser currentUser = firebaseAuth.getCurrentUser();
        if (currentUser == null || currentUser.isAnonymous()) {
            Toast.makeText(this, "로그인 후 공개 과제를 가져올 수 있어요", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, "공개 과제를 가져오는 중입니다", Toast.LENGTH_SHORT).show();
        fetchRootAssignmentsFromFirestore();
    }

    private void fetchRootAssignmentsFromFirestore() {
        firestoreDb.collection(FIRESTORE_ASSIGNMENTS_COLLECTION).whereEqualTo("visibility", "public")
            .get(com.google.firebase.firestore.Source.SERVER)
            .addOnSuccessListener(querySnapshot -> {
                if (isDestroyed() || isFinishing()) return;
                if (querySnapshot.isEmpty()) {
                    Toast.makeText(this, "아직 공개된 수행평가가 없어요", Toast.LENGTH_SHORT).show();
                } else mergeFirestoreAssignments(querySnapshot, "공개 과제");
            })
            .addOnFailureListener(exception -> {
                if (!isDestroyed() && !isFinishing()) Toast.makeText(this, "공개 과제를 가져오지 못했습니다", Toast.LENGTH_SHORT).show();
            });
    }

    private void mergeFirestoreAssignments(QuerySnapshot querySnapshot, String sourceLabel) {
        Map<String, Assignment> localById = new HashMap<>();
        for (Assignment local : assignments) {
            if (!local.cloudId.isEmpty()) {
                localById.put(local.cloudId, local);
            }
        }

        int added = 0;
        int updated = 0;
        for (DocumentSnapshot document : querySnapshot.getDocuments()) {
            Assignment remote = assignmentFromDocument(document);
            if (AssignmentSync.get(this).hasPending(document.getId())) continue;
            if (remote.title.isEmpty()) {
                continue;
            }
            Assignment local = localById.get(remote.cloudId);
            if (local == null) {
                assignments.add(remote);
                localById.put(remote.cloudId, remote);
                added++;
            } else {
                if (local.isPublic) copyAssignment(remote, local);
                updated++;
            }
        }

        saveAssignments();
        refreshUi();
        Toast.makeText(this, sourceLabel + " " + added + "개를 추가하고 " + updated + "개를 업데이트했어요", Toast.LENGTH_SHORT).show();
    }

    private Assignment assignmentFromDocument(DocumentSnapshot document) {
        Assignment assignment = new Assignment();
        assignment.id = "cloud-" + document.getId();
        assignment.cloudId = document.getId();
        assignment.ownerUid = safeString(document.getString("ownerUid"));
        assignment.isPublic = true;
        assignment.title = safeString(document.getString("title"));
        assignment.subject = safeString(document.getString("subject"));
        assignment.detail = safeString(document.getString("detail"));
        assignment.period = safeString(document.getString("period"));
        if (assignment.period.isEmpty()) {
            assignment.period = "1교시";
        }
        assignment.dueMillis = readDueMillis(document);
        Boolean complete = document.getBoolean("complete");
        assignment.complete = complete != null && complete;

        return assignment;
    }

    private long readDueMillis(DocumentSnapshot document) {
        Object dueMillis = document.get("dueMillis");
        if (dueMillis instanceof Number) {
            return ((Number) dueMillis).longValue();
        }
        if (dueMillis instanceof Timestamp) {
            return ((Timestamp) dueMillis).toDate().getTime();
        }

        Object dueDate = document.get("dueDate");
        if (dueDate instanceof Number) {
            return ((Number) dueDate).longValue();
        }
        if (dueDate instanceof Timestamp) {
            return ((Timestamp) dueDate).toDate().getTime();
        }
        return startOfToday().getTimeInMillis();
    }

    private void copyAssignment(Assignment source, Assignment target) {
        // Keep the local ID (draft references) and device-only photos stable on refresh.
        target.cloudId = source.cloudId;
        target.ownerUid = source.ownerUid;
        target.isPublic = source.isPublic;
        target.title = source.title;
        target.subject = source.subject;
        target.detail = source.detail;
        target.period = source.period;
        target.dueMillis = source.dueMillis;
        target.complete = source.complete;
    }

    private String safeString(String value) {
        return value == null ? "" : value;
    }

    private void loadAssignments() {
        assignments.clear();
        try {
            JSONArray array = new JSONArray(prefs.getString(KEY_ASSIGNMENTS, "[]"));
            for (int i = 0; i < array.length(); i++) {
                assignments.add(Assignment.fromJson(array.getJSONObject(i)));
            }
        } catch (JSONException ignored) {
            assignments.clear();
        }
    }

    private void saveAssignments() {
        JSONArray array = new JSONArray();
        for (Assignment assignment : assignments) {
            array.put(assignment.toJson());
        }
        prefs.edit().putString(KEY_ASSIGNMENTS, array.toString()).apply();
        ReminderScheduler.reschedule(this);
    }

    private boolean ownsPublication(Assignment assignment) {
        FirebaseUser user = firebaseAuth.getCurrentUser();
        return user != null && !user.isAnonymous() && user.getUid().equals(assignment.ownerUid);
    }

    private boolean canManagePublication(Assignment assignment) {
        if (isLocalOnly(assignment) || ownsPublication(assignment)) return true;
        Toast.makeText(this, "공개 과제를 등록한 계정으로 로그인해 주세요", Toast.LENGTH_LONG).show();
        return false;
    }

    private boolean savePublication(Assignment changed, boolean deleting) {
        Map<String, Object> payload = null;
        if (changed.isPublic && !deleting) {
            try {
                payload = AssignmentPublication.fields(changed.cloudId, changed.ownerUid, changed.title,
                    changed.subject, changed.period, changed.detail, changed.dueMillis, changed.complete);
            } catch (IllegalArgumentException error) { return false; }
        }
        JSONArray array = new JSONArray();
        for (Assignment item : assignments) array.put(item.toJson());
        AssignmentSync sync = AssignmentSync.get(this);
        if (!sync.save(array, changed.cloudId, changed.ownerUid, payload)) return false;
        ReminderScheduler.reschedule(this);
        sync.retry();
        return true;
    }

    private boolean isLocalOnly(Assignment assignment) {
        return assignment.cloudId.isEmpty() || (!assignment.isPublic
            && !AssignmentSync.get(this).hasPending(assignment.cloudId));
    }

    private void detachUnpublished(Assignment assignment) {
        // A private conversion has become purely local only after deletion acknowledgement.
        if (!assignment.isPublic && isLocalOnly(assignment)) {
            assignment.cloudId = "";
            assignment.ownerUid = "";
        }
    }

    private String dDayText(long dueMillis) {
        long diff = (startOfDay(dueMillis).getTimeInMillis() - startOfToday().getTimeInMillis()) / 86_400_000L;
        if (diff == 0) {
            return "D-day";
        }
        return diff > 0 ? "D-" + diff : "D+" + Math.abs(diff);
    }

    private Calendar startOfToday() {
        return startOfDay(System.currentTimeMillis());
    }

    private Calendar startOfDay(long millis) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(millis);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar;
    }

    private boolean sameDay(long first, long second) {
        Calendar a = startOfDay(first);
        Calendar b = startOfDay(second);
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
            && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }

    private int calendarDateColor(boolean inVisibleMonth, boolean isSunday, boolean isSaturday, boolean isHoliday) {
        if (!inVisibleMonth) {
            return color(R.color.calendar_muted);
        }
        if (isHoliday || isSunday) {
            return color(R.color.calendar_holiday);
        }
        if (isSaturday) {
            return color(R.color.calendar_saturday);
        }
        return color(R.color.text_primary);
    }

    private HolidayInfo holidayInfo(Calendar day) {
        int year = day.get(Calendar.YEAR);
        int month = day.get(Calendar.MONTH) + 1;
        int date = day.get(Calendar.DAY_OF_MONTH);

        if (month == 1 && date == 1) return new HolidayInfo("신정");
        if (month == 3 && date == 1) return new HolidayInfo("삼일절");
        if (month == 5 && date == 5) return new HolidayInfo("어린이날");
        if (month == 6 && date == 6) return new HolidayInfo("현충일");
        if (month == 7 && date == 17) return new HolidayInfo("제헌절");
        if (month == 8 && date == 15) return new HolidayInfo("광복절");
        if (month == 10 && date == 3) return new HolidayInfo("개천절");
        if (month == 10 && date == 9) return new HolidayInfo("한글날");
        if (month == 12 && date == 25) return new HolidayInfo("성탄절");

        String fixedKey = String.format(Locale.US, "%04d-%02d-%02d", year, month, date);
        switch (fixedKey) {
            case "2025-01-28": return new HolidayInfo("설연휴");
            case "2025-01-29": return new HolidayInfo("설날");
            case "2025-01-30": return new HolidayInfo("설연휴");
            case "2025-03-03": return new HolidayInfo("대체");
            case "2025-05-06": return new HolidayInfo("대체");
            case "2025-10-05": return new HolidayInfo("추석연휴");
            case "2025-10-06": return new HolidayInfo("추석");
            case "2025-10-07": return new HolidayInfo("추석연휴");
            case "2025-10-08": return new HolidayInfo("대체");

            case "2026-02-16": return new HolidayInfo("설연휴");
            case "2026-02-17": return new HolidayInfo("설날");
            case "2026-02-18": return new HolidayInfo("설연휴");
            case "2026-03-02": return new HolidayInfo("대체");
            case "2026-05-24": return new HolidayInfo("부처님");
            case "2026-05-25": return new HolidayInfo("대체");
            case "2026-06-03": return new HolidayInfo("지방선거");
            case "2026-08-17": return new HolidayInfo("대체");
            case "2026-09-24": return new HolidayInfo("추석연휴");
            case "2026-09-25": return new HolidayInfo("추석");
            case "2026-09-26": return new HolidayInfo("추석연휴");
            case "2026-10-05": return new HolidayInfo("대체");

            case "2027-02-06": return new HolidayInfo("설연휴");
            case "2027-02-07": return new HolidayInfo("설날");
            case "2027-02-08": return new HolidayInfo("설연휴");
            case "2027-02-09": return new HolidayInfo("대체");
            case "2027-05-13": return new HolidayInfo("부처님");
            case "2027-06-07": return new HolidayInfo("대체");
            case "2027-08-16": return new HolidayInfo("대체");
            case "2027-09-14": return new HolidayInfo("추석연휴");
            case "2027-09-15": return new HolidayInfo("추석");
            case "2027-09-16": return new HolidayInfo("추석연휴");
            case "2027-10-04": return new HolidayInfo("대체");
            case "2027-10-11": return new HolidayInfo("대체");
            case "2027-12-27": return new HolidayInfo("대체");

            case "2028-01-25": return new HolidayInfo("설연휴");
            case "2028-01-26": return new HolidayInfo("설날");
            case "2028-01-27": return new HolidayInfo("설연휴");
            case "2028-05-02": return new HolidayInfo("부처님");
            case "2028-10-02": return new HolidayInfo("추석연휴");
            case "2028-10-03": return new HolidayInfo("추석");
            case "2028-10-04": return new HolidayInfo("추석연휴");
            case "2028-10-05": return new HolidayInfo("대체");
            default: return null;
        }
    }

    private boolean setSpinnerValue(Spinner spinner, String value) {
        for (int i = 0; i < spinner.getCount(); i++) {
            if (spinner.getItemAtPosition(i).toString().equals(value)) {
                spinner.setSelection(i);
                return true;
            }
        }
        return false;
    }

    private LinearLayout card() {
        return surfaceCard(color(R.color.surface_primary), color(R.color.outline));
    }

    private LinearLayout surfaceCard(int backgroundColor, int strokeColor) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(20), dp(20), dp(20), dp(20));
        card.setBackground(roundRect(backgroundColor, strokeColor, dp(24)));
        card.setElevation(dp(1));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(14);
        card.setLayoutParams(params);
        return card;
    }

    private View quickActionCard(int iconRes, String titleValue, String description, int backgroundColor, int foregroundColor, Runnable action) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackground(roundRect(backgroundColor, backgroundColor == color(R.color.surface_primary) ? color(R.color.outline) : 0, dp(24)));
        card.setElevation(dp(1));
        card.setClickable(true);
        card.setFocusable(true);
        card.setContentDescription(titleValue + ", " + description);

        ImageView icon = new ImageView(this);
        icon.setImageDrawable(tintedDrawable(iconRes, foregroundColor));
        icon.setPadding(dp(10), dp(10), dp(10), dp(10));
        int iconSurface = foregroundColor == Color.WHITE ? 0x33FFFFFF : 0x22FFFFFF;
        if (backgroundColor == color(R.color.surface_primary)) {
            iconSurface = color(R.color.lavender_soft);
        }
        icon.setBackground(circle(iconSurface));
        card.addView(icon, new LinearLayout.LayoutParams(dp(44), dp(44)));

        View spacer = new View(this);
        card.addView(spacer, new LinearLayout.LayoutParams(1, 0, 1));
        TextView title = text(titleValue, 16, foregroundColor, Typeface.BOLD);
        card.addView(title);
        TextView subtitle = text(description, 12, foregroundColor == Color.WHITE ? 0xCCFFFFFF : color(R.color.text_secondary), Typeface.NORMAL);
        subtitle.setPadding(0, dp(4), 0, 0);
        subtitle.setMaxLines(1);
        card.addView(subtitle);
        card.setOnClickListener(view -> action.run());
        return card;
    }

    private TextView sectionTitle(String value) {
        TextView title = text(value, 23, color(R.color.text_primary), Typeface.BOLD);
        title.setPadding(0, 0, 0, dp(8));
        return title;
    }

    private TextView eyebrow(String value) {
        TextView view = text(value, 12, color(R.color.text_secondary), Typeface.BOLD);
        view.setPadding(0, 0, 0, dp(6));
        return view;
    }

    private TextView label(String value) {
        TextView label = text(value, 13, color(R.color.text_primary), Typeface.BOLD);
        label.setPadding(0, dp(10), 0, dp(6));
        return label;
    }

    private TextView errorText(String value) {
        TextView view = text(value, 12, color(R.color.danger), Typeface.BOLD);
        view.setPadding(dp(4), 0, dp(4), dp(8));
        view.setVisibility(View.GONE);
        return view;
    }

    private TextView text(String value, int sp, int color, int style) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, style);
        view.setIncludeFontPadding(false);
        view.setLineSpacing(dp(2), 1f);
        return view;
    }

    private EditText editText(String hint) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setTextSize(16);
        input.setTextColor(color(R.color.text_primary));
        input.setHintTextColor(color(R.color.text_secondary));
        input.setSingleLine(false);
        input.setMinHeight(dp(52));
        input.setPadding(dp(16), dp(10), dp(16), dp(10));
        input.setBackground(roundRect(color(R.color.surface_soft), 0, dp(16)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(8);
        input.setLayoutParams(params);
        return input;
    }

    private Spinner spinner(String[] items) {
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, items);
        spinner.setAdapter(adapter);
        spinner.setMinimumHeight(dp(52));
        spinner.setPadding(dp(8), 0, dp(8), 0);
        spinner.setBackground(roundRect(color(R.color.surface_soft), 0, dp(16)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(8);
        spinner.setLayoutParams(params);
        return spinner;
    }

    private Button primaryButton(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextColor(Color.WHITE);
        button.setTextSize(16);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        styleAppButton(button, dp(52));
        button.setBackground(pill(color(R.color.charcoal), 0));
        return button;
    }

    private Button secondaryButton(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextColor(color(R.color.text_primary));
        button.setTextSize(14);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        styleAppButton(button, dp(50));
        button.setBackground(pill(color(R.color.surface_soft), 0));
        return button;
    }

    private Button outlinedButton(String value) {
        Button button = secondaryButton(value);
        button.setBackground(pill(color(R.color.surface_primary), color(R.color.outline)));
        return button;
    }

    private Button compactButton(String value) {
        Button button = secondaryButton(value);
        button.setMinWidth(dp(72));
        styleAppButton(button, dp(50));
        button.setPadding(dp(16), dp(2), dp(16), dp(3));
        button.setBackground(pill(color(R.color.lavender_soft), 0));
        return button;
    }

    private ImageButton iconImageButton(int iconRes, String description, int backgroundColor, int iconColor) {
        ImageButton button = new ImageButton(this);
        button.setImageDrawable(tintedDrawable(iconRes, iconColor));
        button.setContentDescription(description);
        button.setScaleType(ImageView.ScaleType.CENTER);
        button.setPadding(dp(12), dp(12), dp(12), dp(12));
        button.setMinimumWidth(dp(48));
        button.setMinimumHeight(dp(48));
        button.setBackground(circle(backgroundColor));
        button.setElevation(dp(1));
        return button;
    }

    private TextView photoRemoveButton() {
        TextView button = new TextView(this);
        button.setText("×");
        button.setTextSize(16);
        button.setTextColor(Color.WHITE);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        button.setIncludeFontPadding(false);
        button.setContentDescription("첨부사진 삭제");
        button.setMinimumWidth(0);
        button.setMinimumHeight(0);
        button.setClickable(true);
        button.setFocusable(true);
        button.setBackground(circle(color(R.color.danger)));
        button.setElevation(dp(1));
        return button;
    }

    private TextView closeIconButton(String description, int backgroundColor, int iconColor) {
        TextView button = new TextView(this);
        button.setText("×");
        button.setTextSize(20);
        button.setTextColor(iconColor);
        button.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
        button.setGravity(Gravity.CENTER);
        button.setIncludeFontPadding(false);
        button.setContentDescription(description);
        button.setMinimumWidth(0);
        button.setMinimumHeight(0);
        button.setClickable(true);
        button.setFocusable(true);
        button.setBackground(circle(backgroundColor));
        button.setElevation(dp(1));
        return button;
    }

    private FrameLayout closeButtonTarget(String description, int backgroundColor, int iconColor) {
        return iconTouchTarget(closeIconButton(description, backgroundColor, iconColor), dp(34), description);
    }

    private FrameLayout iconTouchTarget(View visual, int visualSize, String description) {
        FrameLayout target = new FrameLayout(this);
        target.setContentDescription(description);
        target.setClickable(true);
        target.setFocusable(true);
        target.setMinimumWidth(dp(48));
        target.setMinimumHeight(dp(48));
        visual.setClickable(false);
        visual.setFocusable(false);
        FrameLayout.LayoutParams visualParams = new FrameLayout.LayoutParams(visualSize, visualSize);
        visualParams.gravity = Gravity.CENTER;
        target.addView(visual, visualParams);
        return target;
    }

    private Button actionButton(String value, int backgroundColor, int textColor) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextColor(textColor);
        button.setTextSize(14);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        styleAppButton(button, dp(52));
        button.setBackground(pill(backgroundColor, 0));
        return button;
    }

    private void styleAppButton(Button button, int minHeight) {
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setIncludeFontPadding(false);
        button.setMinHeight(minHeight);
        button.setMinimumHeight(minHeight);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPadding(dp(16), dp(2), dp(16), dp(3));
        button.setElevation(0);
        button.setStateListAnimator(null);
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        return row;
    }

    private GridLayout.LayoutParams cellParams() {
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = 0;
        params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        params.setMargins(0, 0, 0, 0);
        return params;
    }

    private GridLayout.LayoutParams quickActionParams(int leftMargin, int topMargin) {
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = 0;
        params.height = dp(148);
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        params.setMargins(leftMargin, topMargin, 0, 0);
        return params;
    }

    private GradientDrawable circle(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(color);
        return drawable;
    }

    private GradientDrawable pill(int color, int strokeColor) {
        return roundRect(color, strokeColor, dp(100));
    }

    private GradientDrawable border(int strokeColor, int color, int radius) {
        return roundRect(color == 0 ? Color.WHITE : color, strokeColor, radius);
    }

    private GradientDrawable roundRect(int color, int strokeColor, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        if (strokeColor != 0) {
            drawable.setStroke(dp(1), strokeColor);
        }
        return drawable;
    }

    private GradientDrawable dashedRoundRect(int color, int strokeColor, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        drawable.setStroke(dp(1), strokeColor, dp(4), dp(4));
        return drawable;
    }

    private GradientDrawable sidePanelBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color(R.color.surface_primary));
        float radius = dp(24);
        drawable.setCornerRadii(new float[]{radius, radius, 0, 0, 0, 0, radius, radius});
        drawable.setStroke(dp(1), color(R.color.outline));
        return drawable;
    }

    private Drawable tintedDrawable(int drawableRes, int tintColor) {
        Drawable drawable = getDrawable(drawableRes).mutate();
        drawable.setTint(tintColor);
        return drawable;
    }

    private void styleCenteredDialog(Dialog dialog, float widthFraction, int maxWidthDp) {
        styleCenteredDialog(dialog, widthFraction, maxWidthDp, 0f);
    }

    private void styleCenteredDialog(Dialog dialog, float widthFraction, int maxWidthDp, float heightFraction) {
        Window window = dialog.getWindow();
        if (window == null) {
            return;
        }
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.setGravity(Gravity.CENTER);
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        WindowManager.LayoutParams attributes = window.getAttributes();
        attributes.dimAmount = 0.42f;
        window.setAttributes(attributes);
        int width = Math.min((int) (getResources().getDisplayMetrics().widthPixels * widthFraction), dp(maxWidthDp));
        int height = heightFraction > 0f
            ? (int) (getResources().getDisplayMetrics().heightPixels * heightFraction)
            : WindowManager.LayoutParams.WRAP_CONTENT;
        window.setLayout(width, height);
    }

    private int color(int resourceId) {
        return getResources().getColor(resourceId, getTheme());
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static class MaxHeightScrollView extends ScrollView {
        private final int maxHeight;

        MaxHeightScrollView(Context context, int maxHeight) {
            super(context);
            this.maxHeight = maxHeight;
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int cappedHeightSpec = MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST);
            super.onMeasure(widthMeasureSpec, cappedHeightSpec);
        }
    }

    private static class Assignment {
        String id;
        String title = "";
        String subject = "";
        String detail = "";
        String period = "1교시";
        long dueMillis;
        boolean complete;
        // Additive fields: legacy JSON without these remains private and is never uploaded.
        boolean isPublic;
        String cloudId = "";
        String ownerUid = "";
        final List<String> photos = new ArrayList<>();

        JSONObject toJson() {
            JSONObject object = new JSONObject();
            try {
                object.put("id", id);
                object.put("title", title);
                object.put("subject", subject);
                object.put("detail", detail);
                object.put("period", period);
                object.put("dueMillis", dueMillis);
                object.put("complete", complete);
                object.put("visibility", isPublic ? "public" : "private");
                object.put("cloudId", cloudId);
                object.put("ownerUid", ownerUid);
                JSONArray photoArray = new JSONArray();
                for (String photo : photos) {
                    photoArray.put(photo);
                }
                object.put("photos", photoArray);
            } catch (JSONException ignored) {
            }
            return object;
        }

        static Assignment fromJson(JSONObject object) throws JSONException {
            Assignment assignment = new Assignment();
            assignment.id = object.optString("id");
            assignment.title = object.optString("title");
            assignment.subject = object.optString("subject");
            assignment.detail = object.optString("detail");
            assignment.period = object.optString("period", "1교시");
            assignment.dueMillis = object.optLong("dueMillis", System.currentTimeMillis());
            assignment.complete = object.optBoolean("complete", false);
            assignment.isPublic = "public".equals(object.optString("visibility", "private"));
            assignment.cloudId = object.optString("cloudId", "");
            assignment.ownerUid = object.optString("ownerUid", "");
            JSONArray photoArray = object.optJSONArray("photos");
            if (photoArray != null) {
                for (int i = 0; i < photoArray.length(); i++) {
                    assignment.photos.add(photoArray.optString(i));
                }
            }
            return assignment;
        }
    }

    private static class HolidayInfo {
        final String shortName;

        HolidayInfo(String shortName) {
            this.shortName = shortName;
        }
    }
}
