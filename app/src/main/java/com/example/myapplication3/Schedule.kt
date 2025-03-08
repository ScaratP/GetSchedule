package com.example.myapplication3

data class Schedule(
    val id: Int,            //就是id
    val course: String,     //課程名稱
    val date: String,       //星期幾
    var startTime: String,  //課程開始時間
    val endTime: String,    //課程結束時間
    val isolation: String   //教室位置
)
