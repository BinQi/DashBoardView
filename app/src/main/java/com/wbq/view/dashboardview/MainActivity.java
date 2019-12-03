package com.wbq.view.dashboardview;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;

import java.util.Random;

public class MainActivity extends AppCompatActivity {

    DashboardView mDBV;
    DashboardView mDBV1;
    DashboardView mDBV2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mDBV = findViewById(R.id.dbv);
        mDBV1 = findViewById(R.id.dbv1);
        mDBV2 = findViewById(R.id.dbv2);
    }

    @Override
    protected void onResume() {
        super.onResume();

        mDBV.setPercentValueWithAnim(getRandomValue());
        mDBV1.setPercentValueWithAnim(getRandomValue());
        mDBV2.setPercentValueWithAnim(getRandomValue());
    }

    final int getRandomValue() {
        final Random random = new Random();
        return random.nextInt(101);
    }
}
