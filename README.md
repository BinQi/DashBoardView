# DashBoardView
[![License](https://img.shields.io/badge/license-Apache%202-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)

Android DashBoardView

## Preview
![](https://github.com/BinQi/CoverViewPager/blob/master/raw/demo.gif)

## Usage
```Xml
<com.wbq.view.dashboardview.DashboardView
            android:id="@+id/dbv1"
            android:layout_width="200dp"
            android:layout_height="200dp"
            android:layout_marginTop="10dp"
            app:layout_constraintLeft_toLeftOf="@id/dbv"
            app:layout_constraintTop_toBottomOf="@id/dbv"
            app:section="4"
            app:portion="10"
            app:startColor="@color/dbv_start"
            app:primaryColors="@array/ex_primary_colors"
            app:titles="@array/txt_titles" />
```
