package com.zoowii.jpa_utils.jdbcorm.sqlmapper;

import com.google.common.base.Function;
import com.zoowii.jpa_utils.exceptions.JdbcRuntimeException;
import com.zoowii.jpa_utils.jdbcorm.ModelMeta;
import com.zoowii.jpa_utils.jdbcorm.SqlStatementInfo;
import com.zoowii.jpa_utils.query.ParameterBindings;
import com.zoowii.jpa_utils.util.FieldAccessor;
import com.zoowii.jpa_utils.util.IncrementCircleNumber;
import com.zoowii.jpa_utils.util.ListUtil;
import com.zoowii.jpa_utils.util.StringUtil;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * TODO: 使用NamedPrepareStatement
 * 用来映射JAVA orm model的列类型到SQL数据库中的字段类型
 * Created by zoowii on 15/1/26.
 */
public abstract class SqlMapper {
    protected IncrementCircleNumber incrementCircleNumber = new IncrementCircleNumber(0, Long.MAX_VALUE);

    public abstract String getOfInteger();

    public abstract String getOfLong();

    public abstract String getOfString();

    public abstract String getOfBoolean();

    public abstract String getOfString(int length);

    public abstract String getOfText(boolean isLob);

    public abstract String getOfBytes(boolean isLob);

    public abstract String getOfDate();

    public abstract String getOfDateTime();

    public abstract String getOfTimestamp();

    public String get(Class<?> propertyCls, javax.persistence.Column columnAnno, boolean isLob) {
        if (columnAnno != null && !StringUtil.isEmpty(columnAnno.columnDefinition())) {
            return columnAnno.columnDefinition();
        }
        if (propertyCls == Integer.class) {
            return getOfInteger();
        }
        if (propertyCls == Long.class) {
            return getOfLong();
        }
        if (propertyCls == String.class) {
            if (isLob) {
                return getOfText(true);
            }
            if (columnAnno != null) {
                return getOfString(columnAnno.length());
            } else {
                return getOfString();
            }
        }
        if (propertyCls == Boolean.class) {
            return getOfBoolean();
        }
        if (propertyCls == Date.class || propertyCls == java.sql.Time.class) {
            return getOfDateTime();
        }
        if (propertyCls == java.sql.Date.class) {
            return getOfDate();
        }
        if (propertyCls == java.sql.Timestamp.class) {
            return getOfTimestamp();
        }
        if (propertyCls.isArray() && (propertyCls.getComponentType() == Byte.class || propertyCls.getComponentType().getName().equals("byte"))) {
            return getOfBytes(isLob);
        }
        throw new JdbcRuntimeException("Can't find sql column type of column type " + propertyCls.getName());
    }

    /**
     * 为了避免字段名和数据库SQL关键字冲突,wrap一下,比如在mysql中用`columnName`来wrap
     *
     * @param columnName
     * @return
     */
    public String getSqlColumnNameWrapped(String columnName) {
        return columnName;
    }

    /**
     * 为了避免表名和数据库SQL关键字冲突,wrap一下,比如在mysql中用`tableName`来wrap
     *
     * @param tableName
     * @return
     */
    public String getSqlTableNameWrapped(String tableName) {
        return tableName;
    }

    public Pair<String, String> getFromSubSql(ModelMeta modelMeta, boolean useAlias) {
        String tableFullName = StringUtil.isEmpty(
                modelMeta.getTableSchema()) ? getSqlTableNameWrapped(modelMeta.getTableName()) : String.format("%s.%s", modelMeta.getTableSchema(), getSqlTableNameWrapped(modelMeta.getTableName()));
        String tableAlias = useAlias ? String.format("%s_%s", modelMeta.getTableName(), incrementCircleNumber.getAndIncrement() + "") : null;
        String fromSql = useAlias ? String.format(" FROM %s %s ", tableFullName, tableAlias) : String.format(" FROM %s ", tableFullName);
        return Pair.of(fromSql, tableAlias);
    }

    public String getColumnsSql(ModelMeta modelMeta, final String tableAlias, boolean includeId) {
        List<String> columnSqlNames = new ArrayList<String>();
        for (ModelMeta.ModelColumnMeta columnMeta : modelMeta.getColumnMetaSet()) {
            if (columnMeta.isId && !includeId) {
                continue;
            }
            String columnSql = getSqlColumnNameWrapped(columnMeta.columnName);
            columnSqlNames.add(columnSql);
        }
        return StringUtil.join(ListUtil.map(columnSqlNames, new Function<String, String>() {
            @Override
            public String apply(String columnSql) {
                return tableAlias != null ? (tableAlias + "." + columnSql) : columnSql;
            }
        }), ",");
    }

    public SqlStatementInfo getUpdate(final ModelMeta modelMeta, final Object entity, Function<ParameterBindings, String> conditionSqlGenerator) {
        final ParameterBindings parameterBindings = new ParameterBindings();
        String tableFullName = StringUtil.isEmpty(
                modelMeta.getTableSchema()) ? getSqlTableNameWrapped(modelMeta.getTableName()) : String.format("%s.%s", modelMeta.getTableSchema(), getSqlTableNameWrapped(modelMeta.getTableName()));
        List<Pair<String, String>> columnNameAndSqlNames = new ArrayList<Pair<String, String>>(); // left is field name like 'name', and right is name in sql like '{tableAlias}.`name`'
        for (ModelMeta.ModelColumnMeta columnMeta : modelMeta.getColumnMetaSet()) {
            if (columnMeta.isId) {
                continue;
            }
            String columnSql = getSqlColumnNameWrapped(columnMeta.columnName);
            columnNameAndSqlNames.add(Pair.of(columnMeta.fieldName, columnSql));
        }
        final List<String> columnSetPairs = new ArrayList<String>();
        ListUtil.map(columnNameAndSqlNames, new Function<Pair<String, String>, Object>() {
            @Override
            public Object apply(Pair<String, String> pair) {
                FieldAccessor fieldAccessor = new FieldAccessor(modelMeta.getModelCls(), pair.getLeft());
                parameterBindings.addIndexBinding(fieldAccessor.getProperty(entity));
                columnSetPairs.add(String.format("%s = ?", pair.getRight()));
                return null;
            }
        });
        String setItemsSql = StringUtil.join(columnSetPairs, ",");
        String condition;
        if (conditionSqlGenerator != null) {
            condition = conditionSqlGenerator.apply(parameterBindings);
        } else {
            condition = " 1 = 1 ";
        }
        String sql = String.format("UPDATE %s SET %s %s ", tableFullName, setItemsSql, getWhereSubSql(condition));
        return SqlStatementInfo.of(sql, parameterBindings);
    }

    public SqlStatementInfo getInsert(ModelMeta modelMeta, Object entity) {
        String tableFullName = StringUtil.isEmpty(
                modelMeta.getTableSchema()) ? getSqlTableNameWrapped(modelMeta.getTableName()) : String.format("%s.%s", modelMeta.getTableSchema(), getSqlTableNameWrapped(modelMeta.getTableName()));
        List<Pair<String, String>> columnNameAndSqlNames = new ArrayList<Pair<String, String>>(); // left is field name like 'name', and right is name in sql like '{tableAlias}.`name`'
        boolean includeId = true;
        for (ModelMeta.ModelColumnMeta columnMeta : modelMeta.getColumnMetaSet()) {
            if (columnMeta.isId) {
                FieldAccessor fieldAccessor = new FieldAccessor(modelMeta.getModelCls(), columnMeta.fieldName);
                Object idValue = fieldAccessor.getProperty(entity);
                if (idValue == null) {
                    // TODO: use generator strategy to generate new id value
                    includeId = false;
                    continue;
                }
            }
            String columnSql = getSqlColumnNameWrapped(columnMeta.columnName);
            columnNameAndSqlNames.add(Pair.of(columnMeta.fieldName, columnSql));
        }
        ParameterBindings parameterBindings = new ParameterBindings();
        for (Pair<String, String> columnNameAndSqlName : columnNameAndSqlNames) {
            String fieldName = columnNameAndSqlName.getLeft();
            FieldAccessor fieldAccessor = new FieldAccessor(modelMeta.getModelCls(), fieldName);
            parameterBindings.addIndexBinding(fieldAccessor.getProperty(entity));
        }
        String columnsSql = getColumnsSql(modelMeta, null, includeId);
        String valuesBindingSql = StringUtil.join(ListUtil.map(columnNameAndSqlNames, new Function<Pair<String, String>, String>() {
            @Override
            public String apply(Pair<String, String> stringStringPair) {
                return "?"; // String.format(":%s", stringStringPair.getLeft());
            }
        }), ",");
        String sql = String.format("INSERT INTO %s ( %s ) VALUES ( %s )", tableFullName, columnsSql, valuesBindingSql);
        return SqlStatementInfo.of(sql, parameterBindings);
    }

    public String getWhereSubSql(String whereConditions) {
        return " WHERE " + whereConditions;
    }

    public String getEqConditionSubSql(ModelMeta modelMeta, String fieldName, Object value, ParameterBindings parameterBindings, String tableAlias) {
        String columnName = modelMeta.getColumnMetaByFieldName(fieldName).columnName;
        parameterBindings.addIndexBinding(value);
        return String.format(" (%s = ?) ", tableAlias != null ? (tableAlias + "." + getSqlColumnNameWrapped(columnName)) : getSqlColumnNameWrapped(columnName));
    }

    public String getIdEqConditionSubSql(ModelMeta modelMeta, Object value, ParameterBindings parameterBindings, String tableAlias) {
        String idFieldName = modelMeta.getIdColumnMeta().fieldName;
        return getEqConditionSubSql(modelMeta, idFieldName, value, parameterBindings, tableAlias);
    }

    public String getSelectSubSql(String selectContent) {
        return String.format(" SELECT %S ", selectContent);
    }

    public String getDeleteSubSql(String fromSql, String whereSql) {
        return String.format("DELETE %s %s", fromSql, whereSql != null ? whereSql : "");
    }
}
