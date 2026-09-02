package com.reportcalendar.app;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.content.ClipData;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int PHOTO_REQUEST_CODE = 4001;
    private static final String PREFS = "report_calendar_native";
    private static final String KEY_ASSIGNMENTS = "assignments";
    private static final String KEY_REMINDER_DAYS = "reminder_days";
    private static final String KEY_REMINDER_TIME = "reminder_time";

    private final List<Assignment> assignments = new ArrayList<>();
    private final List<Uri> selectedPhotos = new ArrayList<>();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy년 M월 d일 (E)", Locale.KOREAN);
    private final SimpleDateFormat shortDateFormat = new SimpleDateFormat("M월 d일", Locale.KOREAN);

    private SharedPreferences prefs;
    private ScrollView rootScrollView;
    private LinearLayout content;
    private View dashboardSection;
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
    private TextView brandSubtitle;
    private TextView photoCountText;
    private HorizontalScrollView photoPreviewScroll;
    private LinearLayout photoPreviewList;
    private TextView reminderSummary;
    private EditText titleInput;
    private EditText detailInput;
    private EditText customSubjectInput;
    private Spinner subjectSpinner;
    private Spinner periodSpinner;
    private TextView subjectErrorText;
    private TextView periodErrorText;
    private Spinner reminderDaySpinner;
    private Button dueDateButton;
    private Button formToggleButton;
    private Button reminderToggleButton;
    private Calendar selectedDueDate;
    private Calendar visibleMonth;
    private Calendar selectedCalendarDay;
    private Assignment editingAssignment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        selectedDueDate = startOfToday();
        visibleMonth = startOfToday();
        visibleMonth.set(Calendar.DAY_OF_MONTH, 1);
        selectedCalendarDay = startOfToday();
        getWindow().setStatusBarColor(color(R.color.app_background));
        getWindow().setNavigationBarColor(color(R.color.app_background));
        loadAssignments();
        buildUi();
        refreshUi();
    }

    private void buildUi() {
        rootScrollView = new ScrollView(this);
        rootScrollView.setFillViewport(true);
        rootScrollView.setClipToPadding(false);
        rootScrollView.setBackgroundColor(color(R.color.app_background));

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(16), dp(20), dp(40));
        rootScrollView.addView(content, new ScrollView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        content.addView(buildHeader());
        dashboardSection = buildDashboard();
        calendarSection = buildCalendarPanel();
        formSection = buildFormPanel();
        reminderSection = buildReminderPanel();
        content.addView(dashboardSection);
        content.addView(calendarSection);
        content.addView(formSection);
        content.addView(reminderSection);
        setContentView(rootScrollView);
    }

    private View buildHeader() {
        LinearLayout header = row();
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(4), dp(4), dp(4), dp(4));
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

        ImageButton menu = iconImageButton(R.drawable.ic_menu, "메뉴 열기", color(R.color.surface_primary), color(R.color.text_primary));
        menu.setOnClickListener(view -> showNavigationMenu());
        header.addView(menu, new LinearLayout.LayoutParams(dp(48), dp(48)));

        return header;
    }

    private View buildDashboard() {
        LinearLayout dashboard = new LinearLayout(this);
        dashboard.setOrientation(LinearLayout.VERTICAL);
        dashboard.setPadding(0, 0, 0, dp(22));

        LinearLayout nearestCard = surfaceCard(color(R.color.lavender_soft), 0);
        nearestCard.addView(eyebrow("가장 가까운 수행평가"));
        nearestText = text("", 21, color(R.color.text_primary), Typeface.BOLD);
        nearestCard.addView(nearestText);
        nearestMetaText = text("", 13, color(R.color.text_secondary), Typeface.NORMAL);
        nearestMetaText.setPadding(0, dp(8), 0, 0);
        nearestCard.addView(nearestMetaText);
        dashboard.addView(nearestCard);

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
            quickActionCard(R.drawable.ic_week, "이번 주", "이번 주 마감 확인", color(R.color.butter_yellow), color(R.color.text_primary), () -> scrollTo(calendarSection)),
            quickActionParams(dp(8), 0)
        );
        quickActions.addView(
            quickActionCard(R.drawable.ic_calendar, "캘린더", "월간 일정 한눈에", color(R.color.charcoal), Color.WHITE, () -> scrollTo(calendarSection)),
            quickActionParams(0, dp(8))
        );
        quickActions.addView(
            quickActionCard(R.drawable.ic_notifications, "알림 설정", "놓치지 않게 준비", color(R.color.surface_primary), color(R.color.text_primary), this::openReminderAndScroll),
            quickActionParams(dp(8), dp(8))
        );

        return dashboard;
    }

    private View buildFormPanel() {
        LinearLayout panel = card();
        panel.addView(eyebrow("일정 관리"));
        LinearLayout head = row();
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView heading = sectionTitle("수행평가 등록");
        head.addView(heading, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
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

        Button photoButton = secondaryButton("사진 업로드");
        photoButton.setCompoundDrawablesWithIntrinsicBounds(tintedDrawable(R.drawable.ic_photo, color(R.color.text_primary)), null, null, null);
        photoButton.setCompoundDrawablePadding(dp(8));
        photoButton.setOnClickListener(view -> pickPhotos());
        formBody.addView(label("첨부사진"));
        formBody.addView(photoButton);

        photoCountText = text("사진 중복 업로드 가능", 13, color(R.color.text_secondary), Typeface.NORMAL);
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

        Button saveButton = primaryButton("저장");
        saveButton.setOnClickListener(view -> saveAssignmentFromForm());
        formBody.addView(saveButton);

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
        panel.addView(buildWeekPanel());

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
        TextView description = text("알림 기준과 시간을 저장해요.", 13, color(R.color.text_secondary), Typeface.NORMAL);
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

        reminderDaySpinner = spinner(new String[]{"1일 전", "2일 전", "3일 전", "7일 전"});
        reminderBody.addView(label("알림 시작일"));
        reminderBody.addView(reminderDaySpinner);

        Button timeButton = secondaryButton("알림 시간 선택");
        timeButton.setOnClickListener(view -> pickReminderTime());
        reminderBody.addView(label("알림 시간"));
        reminderBody.addView(timeButton);

        LinearLayout actions = row();
        Button save = primaryButton("저장");
        save.setOnClickListener(view -> {
            prefs.edit()
                .putInt(KEY_REMINDER_DAYS, reminderDaySpinner.getSelectedItemPosition())
                .apply();
            Toast.makeText(this, "저장되었습니다", Toast.LENGTH_SHORT).show();
            refreshReminderSummary();
        });
        actions.addView(save, new LinearLayout.LayoutParams(0, dp(48), 1));

        Button cancel = secondaryButton("취소");
        cancel.setOnClickListener(view -> {
            prefs.edit().remove(KEY_REMINDER_DAYS).remove(KEY_REMINDER_TIME).apply();
            Toast.makeText(this, "취소되었습니다", Toast.LENGTH_SHORT).show();
            refreshReminderSummary();
        });
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(0, dp(48), 1);
        cancelParams.leftMargin = dp(8);
        actions.addView(cancel, cancelParams);
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        actionParams.topMargin = dp(12);
        reminderBody.addView(actions, actionParams);

        reminderSummary = text("", 13, color(R.color.text_secondary), Typeface.NORMAL);
        reminderSummary.setPadding(0, dp(10), 0, 0);
        reminderBody.addView(reminderSummary);
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

        Assignment assignment = editingAssignment == null ? new Assignment() : editingAssignment;
        assignment.id = assignment.id == null ? String.valueOf(System.currentTimeMillis()) : assignment.id;
        assignment.title = title;
        assignment.subject = subject;
        assignment.detail = detailInput.getText().toString().trim();
        assignment.period = period;
        assignment.dueMillis = selectedDueDate.getTimeInMillis();
        assignment.photos.clear();
        for (Uri uri : selectedPhotos) {
            assignment.photos.add(uri.toString());
        }

        if (editingAssignment == null) {
            assignments.add(assignment);
        }

        editingAssignment = null;
        saveAssignments();
        clearForm();
        refreshUi();
        Toast.makeText(this, "저장되었습니다", Toast.LENGTH_SHORT).show();
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
        new TimePickerDialog(
            this,
            (view, hourOfDay, minute) -> {
                String value = String.format(Locale.KOREAN, "%02d:%02d", hourOfDay, minute);
                prefs.edit().putString(KEY_REMINDER_TIME, value).apply();
                refreshReminderSummary();
            },
            now.get(Calendar.HOUR_OF_DAY),
            now.get(Calendar.MINUTE),
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
        photoCountText.setText(selectedPhotos.isEmpty() ? "사진 중복 업로드 가능" : selectedPhotos.size() + "장 선택됨");
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
            ImageView image = new ImageView(this);
            image.setImageURI(uri);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setContentDescription("선택한 사진 미리보기");
            image.setBackground(roundRect(color(R.color.surface_soft), 0, dp(16)));
            image.setClipToOutline(true);
            image.setOnClickListener(view -> openPhoto(uri));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(88), dp(88));
            params.rightMargin = dp(8);
            photoPreviewList.addView(image, params);
        }
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
        Assignment nearest = null;
        for (Assignment assignment : assignments) {
            if (assignment.complete) {
                continue;
            }
            if (nearest == null || assignment.dueMillis < nearest.dueMillis) {
                nearest = assignment;
            }
        }
        if (nearest == null) {
            nearestText.setText("예정된 수행평가가 없어요");
            nearestMetaText.setText("등록 버튼으로 첫 일정을 추가해 보세요.");
        } else {
            nearestText.setText(nearest.title);
            nearestMetaText.setText(nearest.subject + " · " + nearest.period + " · " + shortDateFormat.format(nearest.dueMillis) + " · " + dDayText(nearest.dueMillis));
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
        weekItems.sort(Comparator.comparingLong(item -> item.dueMillis));
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

        for (Assignment assignment : weekItems) {
            weekList.addView(assignmentRow(assignment));
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
        LinearLayout row = surfaceCard(assignment.complete ? color(R.color.completed) : color(R.color.surface_primary), color(R.color.outline));
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
        copy.addView(text("이 날짜의 수행평가", 21, color(R.color.text_primary), Typeface.BOLD));
        TextView count = text(dayAssignments.size() + "개의 일정이 있어요", 13, color(R.color.text_secondary), Typeface.NORMAL);
        count.setPadding(0, dp(6), 0, 0);
        copy.addView(count);
        head.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        ImageButton close = iconImageButton(R.drawable.ic_close, "안내창 닫기", color(R.color.surface_soft), color(R.color.text_primary));
        close.setOnClickListener(view -> dialog.dismiss());
        head.addView(close, new LinearLayout.LayoutParams(dp(48), dp(48)));
        card.addView(head);

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        listParams.topMargin = dp(18);
        card.addView(list, listParams);

        for (Assignment assignment : dayAssignments) {
            list.addView(dayDialogRow(assignment, dialog));
        }

        dialog.setContentView(card);
        styleCenteredDialog(dialog, 0.92f, 420);
        dialog.show();
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
        ImageButton close = iconImageButton(R.drawable.ic_close, "상세 정보 닫기", color(R.color.surface_soft), color(R.color.text_primary));
        close.setOnClickListener(view -> dialog.dismiss());
        head.addView(close, new LinearLayout.LayoutParams(dp(48), dp(48)));
        card.addView(head);

        TextView meta = text(assignment.subject + " · " + assignment.period + " · " + dateFormat.format(assignment.dueMillis), 13, color(R.color.text_secondary), Typeface.NORMAL);
        meta.setPadding(0, dp(10), 0, 0);
        card.addView(meta);

        TextView dday = text(dDayText(assignment.dueMillis), 13, color(R.color.text_primary), Typeface.BOLD);
        dday.setPadding(dp(12), dp(6), dp(12), dp(6));
        dday.setBackground(pill(assignment.complete ? color(R.color.completed) : color(R.color.butter_yellow), 0));
        LinearLayout.LayoutParams ddayParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ddayParams.topMargin = dp(14);
        card.addView(dday, ddayParams);

        if (!assignment.detail.isEmpty()) {
            TextView detailLabel = eyebrow("세부 내용");
            detailLabel.setPadding(0, dp(20), 0, dp(8));
            card.addView(detailLabel);
            TextView detail = text(assignment.detail, 15, color(R.color.text_primary), Typeface.NORMAL);
            detail.setPadding(dp(16), dp(14), dp(16), dp(14));
            detail.setBackground(roundRect(color(R.color.surface_soft), 0, dp(18)));
            card.addView(detail);
        }

        if (!assignment.photos.isEmpty()) {
            TextView photoLabel = eyebrow("첨부사진");
            photoLabel.setPadding(0, dp(20), 0, dp(8));
            card.addView(photoLabel);
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
            card.addView(scroll);
        }

        LinearLayout actions = row();
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        actionsParams.topMargin = dp(22);
        Button delete = actionButton("삭제", color(R.color.danger_soft), color(R.color.danger));
        delete.setOnClickListener(view -> confirmDelete(assignment, dialog));
        actions.addView(delete, new LinearLayout.LayoutParams(0, dp(52), 1));
        Button edit = actionButton("수정", color(R.color.lavender_soft), color(R.color.text_primary));
        edit.setOnClickListener(view -> {
            dialog.dismiss();
            editAssignment(assignment);
        });
        LinearLayout.LayoutParams editParams = new LinearLayout.LayoutParams(0, dp(52), 1);
        editParams.leftMargin = dp(8);
        actions.addView(edit, editParams);
        Button complete = actionButton(assignment.complete ? "미완료" : "완료", color(R.color.charcoal), Color.WHITE);
        complete.setOnClickListener(view -> {
            assignment.complete = !assignment.complete;
            saveAssignments();
            refreshUi();
            dialog.dismiss();
        });
        LinearLayout.LayoutParams completeParams = new LinearLayout.LayoutParams(0, dp(52), 1);
        completeParams.leftMargin = dp(8);
        actions.addView(complete, completeParams);
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
        TextView message = text("삭제한 수행평가는 다시 복구할 수 없습니다.", 14, color(R.color.text_secondary), Typeface.NORMAL);
        message.setPadding(0, dp(10), 0, dp(20));
        card.addView(message);
        LinearLayout actions = row();
        Button cancel = actionButton("취소", color(R.color.surface_soft), color(R.color.text_primary));
        cancel.setOnClickListener(view -> confirm.dismiss());
        actions.addView(cancel, new LinearLayout.LayoutParams(0, dp(52), 1));
        Button delete = actionButton("삭제", color(R.color.danger), Color.WHITE);
        delete.setOnClickListener(view -> {
            assignments.remove(assignment);
            saveAssignments();
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
        editingAssignment = assignment;
        titleInput.setText(assignment.title);
        detailInput.setText(assignment.detail);
        selectedPhotos.clear();
        for (String photo : assignment.photos) {
            selectedPhotos.add(Uri.parse(photo));
        }
        photoCountText.setText(selectedPhotos.isEmpty() ? "사진 중복 업로드 가능" : selectedPhotos.size() + "장 선택됨");
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
        ImageButton close = iconImageButton(R.drawable.ic_close, "메뉴 닫기", color(R.color.surface_soft), color(R.color.text_primary));
        close.setOnClickListener(view -> dialog.dismiss());
        head.addView(close, new LinearLayout.LayoutParams(dp(48), dp(48)));
        panel.addView(head);

        TextView mainLabel = eyebrow("주요 화면");
        mainLabel.setPadding(0, dp(28), 0, dp(8));
        panel.addView(mainLabel);
        panel.addView(sideMenuButton(R.drawable.ic_week, "이번 주 수행평가", dialog, () -> scrollTo(calendarSection)));
        panel.addView(sideMenuButton(R.drawable.ic_calendar, "캘린더 보기", dialog, () -> scrollTo(calendarSection)));

        TextView settingLabel = eyebrow("설정");
        settingLabel.setPadding(0, dp(24), 0, dp(8));
        panel.addView(settingLabel);
        panel.addView(sideMenuButton(R.drawable.ic_assignment, "수행평가 등록", dialog, this::openFormAndScroll));
        panel.addView(sideMenuButton(R.drawable.ic_notifications, "알림 설정", dialog, this::openReminderAndScroll));

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
        if (formBody != null) {
            formBody.setVisibility(View.VISIBLE);
            formToggleButton.setText("접기");
        }
        scrollTo(formSection);
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
        subjectErrorText.setVisibility(View.GONE);
        periodErrorText.setVisibility(View.GONE);
        selectedPhotos.clear();
        selectedDueDate = startOfToday();
        photoCountText.setText("사진 중복 업로드 가능");
        refreshPhotoPreview();
    }

    private void refreshReminderSummary() {
        if (!prefs.contains(KEY_REMINDER_DAYS) && !prefs.contains(KEY_REMINDER_TIME)) {
            reminderSummary.setText("설정된 알림이 없습니다");
            return;
        }
        String day = reminderDaySpinner.getItemAtPosition(prefs.getInt(KEY_REMINDER_DAYS, 0)).toString();
        String time = prefs.getString(KEY_REMINDER_TIME, "시간 미선택");
        reminderSummary.setText(day + " · " + time);
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
        button.setAllCaps(false);
        button.setMinHeight(dp(52));
        button.setBackground(pill(color(R.color.charcoal), 0));
        return button;
    }

    private Button secondaryButton(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextColor(color(R.color.text_primary));
        button.setTextSize(14);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setAllCaps(false);
        button.setMinHeight(dp(48));
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
        button.setMinimumHeight(dp(48));
        button.setPadding(dp(16), 0, dp(16), 0);
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

    private Button actionButton(String value, int backgroundColor, int textColor) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextColor(textColor);
        button.setTextSize(14);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setAllCaps(false);
        button.setMinHeight(dp(52));
        button.setBackground(pill(backgroundColor, 0));
        return button;
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
        window.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT);
    }

    private int color(int resourceId) {
        return getResources().getColor(resourceId, getTheme());
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static class Assignment {
        String id;
        String title = "";
        String subject = "";
        String detail = "";
        String period = "1교시";
        long dueMillis;
        boolean complete;
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
