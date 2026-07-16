package com.vone.vmq;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;

public final class PaymentPushQueue {
    private static final String DB_NAME = "payment_push_queue.db";
    private static final int DB_VERSION = 1;
    private static final int MAX_ATTEMPTS = 10;

    private PaymentPushQueue() {}

    public static long enqueue(Context context, int type, String price) {
        if (TextUtils.isEmpty(price)) {
            return -1L;
        }
        SQLiteDatabase db = helper(context).getWritableDatabase();
        android.content.ContentValues values = new android.content.ContentValues();
        values.put("pay_type", type);
        values.put("price", price);
        values.put("created_at", System.currentTimeMillis());
        values.put("attempts", 0);
        values.put("last_error", "");
        return db.insert("payment_push", null, values);
    }

    public static List<Item> pending(Context context, int limit) {
        SQLiteDatabase db = helper(context).getReadableDatabase();
        List<Item> items = new ArrayList<>();
        Cursor cursor = db.query(
                "payment_push",
                new String[]{"id", "pay_type", "price", "created_at", "attempts"},
                "attempts < ?",
                new String[]{String.valueOf(MAX_ATTEMPTS)},
                null,
                null,
                "id ASC",
                String.valueOf(limit));
        try {
            while (cursor.moveToNext()) {
                items.add(new Item(
                        cursor.getLong(0),
                        cursor.getInt(1),
                        cursor.getString(2),
                        cursor.getLong(3),
                        cursor.getInt(4)));
            }
        } finally {
            cursor.close();
        }
        return items;
    }

    public static void markSuccess(Context context, long id) {
        helper(context).getWritableDatabase().delete(
                "payment_push",
                "id = ?",
                new String[]{String.valueOf(id)});
    }

    public static void markFailure(Context context, long id, String error) {
        SQLiteDatabase db = helper(context).getWritableDatabase();
        android.content.ContentValues values = new android.content.ContentValues();
        values.put("last_error", error == null ? "" : error);
        db.update(
                "payment_push",
                values,
                "id = ?",
                new String[]{String.valueOf(id)});
        db.execSQL("UPDATE payment_push SET attempts = attempts + 1 WHERE id = ?",
                new Object[]{id});
    }

    private static Helper helper(Context context) {
        return new Helper(context.getApplicationContext());
    }

    public static final class Item {
        public final long id;
        public final int type;
        public final String price;
        public final long createdAt;
        public final int attempts;

        Item(long id, int type, String price, long createdAt, int attempts) {
            this.id = id;
            this.type = type;
            this.price = price;
            this.createdAt = createdAt;
            this.attempts = attempts;
        }
    }

    private static final class Helper extends SQLiteOpenHelper {
        Helper(Context context) {
            super(context, DB_NAME, null, DB_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE payment_push ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "pay_type INTEGER NOT NULL,"
                    + "price TEXT NOT NULL,"
                    + "created_at INTEGER NOT NULL,"
                    + "attempts INTEGER NOT NULL DEFAULT 0,"
                    + "last_error TEXT NOT NULL DEFAULT ''"
                    + ")");
            db.execSQL("CREATE INDEX idx_payment_push_pending ON payment_push(attempts, id)");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        }
    }
}
