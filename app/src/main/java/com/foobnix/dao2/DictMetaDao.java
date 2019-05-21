package com.foobnix.dao2;

import android.database.Cursor;
import android.database.sqlite.SQLiteStatement;

import org.greenrobot.greendao.AbstractDao;
import org.greenrobot.greendao.Property;
import org.greenrobot.greendao.database.Database;
import org.greenrobot.greendao.database.DatabaseStatement;
import org.greenrobot.greendao.internal.DaoConfig;

// THIS CODE IS GENERATED BY greenDAO, DO NOT EDIT.
/** 
 * DAO for table "DICT_META".
*/
public class DictMetaDao extends AbstractDao<DictMeta, String> {

    public static final String TABLENAME = "DICT_META";

    /**
     * Properties of entity DictMeta.<br/>
     * Can be used for QueryBuilder and for referencing column names.
     */
    public static class Properties {
        public final static Property Key = new Property(0, String.class, "key", true, "KEY");
        public final static Property Value = new Property(1, String.class, "value", false, "VALUE");
    }


    public DictMetaDao(DaoConfig config) {
        super(config);
    }
    
    public DictMetaDao(DaoConfig config, DaoSession daoSession) {
        super(config, daoSession);
    }

    /** Creates the underlying database table. */
    public static void createTable(Database db, boolean ifNotExists) {
        String constraint = ifNotExists? "IF NOT EXISTS ": "";
        db.execSQL("CREATE TABLE " + constraint + "\"DICT_META\" (" + //
                "\"KEY\" TEXT PRIMARY KEY NOT NULL ," + // 0: key
                "\"VALUE\" TEXT);"); // 1: value
    }

    /** Drops the underlying database table. */
    public static void dropTable(Database db, boolean ifExists) {
        String sql = "DROP TABLE " + (ifExists ? "IF EXISTS " : "") + "\"DICT_META\"";
        db.execSQL(sql);
    }

    @Override
    protected final void bindValues(DatabaseStatement stmt, DictMeta entity) {
        stmt.clearBindings();
 
        String key = entity.getKey();
        if (key != null) {
            stmt.bindString(1, key);
        }
 
        String value = entity.getValue();
        if (value != null) {
            stmt.bindString(2, value);
        }
    }

    @Override
    protected final void bindValues(SQLiteStatement stmt, DictMeta entity) {
        stmt.clearBindings();
 
        String key = entity.getKey();
        if (key != null) {
            stmt.bindString(1, key);
        }
 
        String value = entity.getValue();
        if (value != null) {
            stmt.bindString(2, value);
        }
    }

    @Override
    public String readKey(Cursor cursor, int offset) {
        return cursor.isNull(offset + 0) ? null : cursor.getString(offset + 0);
    }    

    @Override
    public DictMeta readEntity(Cursor cursor, int offset) {
        DictMeta entity = new DictMeta( //
            cursor.isNull(offset + 0) ? null : cursor.getString(offset + 0), // key
            cursor.isNull(offset + 1) ? null : cursor.getString(offset + 1) // value
        );
        return entity;
    }
     
    @Override
    public void readEntity(Cursor cursor, DictMeta entity, int offset) {
        entity.setKey(cursor.isNull(offset + 0) ? null : cursor.getString(offset + 0));
        entity.setValue(cursor.isNull(offset + 1) ? null : cursor.getString(offset + 1));
     }
    
    @Override
    protected final String updateKeyAfterInsert(DictMeta entity, long rowId) {
        return entity.getKey();
    }
    
    @Override
    public String getKey(DictMeta entity) {
        if(entity != null) {
            return entity.getKey();
        } else {
            return null;
        }
    }

    @Override
    public boolean hasKey(DictMeta entity) {
        return entity.getKey() != null;
    }

    @Override
    protected final boolean isEntityUpdateable() {
        return true;
    }
    
}
