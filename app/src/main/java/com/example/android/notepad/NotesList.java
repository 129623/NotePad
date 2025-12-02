package com.example.android.notepad;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.tabs.TabLayout;

public class NotesList extends AppCompatActivity {

    private static final int REQUEST_CODE_EDIT_NOTE = 1;
    private ViewPager viewPager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.noteslist_expandable);

        viewPager = findViewById(R.id.view_pager);
        viewPager.setAdapter(new ViewPagerAdapter(getSupportFragmentManager()));

        TabLayout tabLayout = findViewById(R.id.tabs);
        tabLayout.setupWithViewPager(viewPager);

        findViewById(R.id.fab_add_note).setOnClickListener(v -> {
            // 检查当前选中的标签页
            int currentItem = viewPager.getCurrentItem();
            if (currentItem == 0) {
                // 笔记页面 - 打开笔记编辑器
                Intent intent = new Intent(Intent.ACTION_INSERT, NotePad.Notes.CONTENT_URI);
                intent.setClassName(NotesList.this, "com.example.android.notepad.NoteEditor");
                startActivityForResult(intent, REQUEST_CODE_EDIT_NOTE);
            } else if (currentItem == 1) {
                // 待办事项页面 - 通过TodoFragment处理
                Fragment fragment = getSupportFragmentManager().findFragmentByTag("android:switcher:" + R.id.view_pager + ":" + viewPager.getCurrentItem());
                if (fragment instanceof TodoFragment) {
                    ((TodoFragment) fragment).showAddTodoDialog();
                }
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_EDIT_NOTE && resultCode == RESULT_OK) {
            // The view pager and its fragments will handle their own refresh.
            // We can trigger it here if necessary, but it's often better to handle it within the fragment.
        }
    }
}