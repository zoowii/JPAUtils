package com.zoowii.jpa_utils.query;

import com.google.common.base.Function;
import com.zoowii.jpa_utils.core.IWrappedQuery;
import com.zoowii.jpa_utils.core.IWrappedTypedQuery;
import com.zoowii.jpa_utils.core.Session;
import com.zoowii.jpa_utils.orm.Model;
import com.zoowii.jpa_utils.util.ListUtil;
import com.zoowii.jpa_utils.util.ModelUtils;
import com.zoowii.jpa_utils.util.StringUtil;
import com.zoowii.jpa_utils.util.functions.Function2;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Query<M> {
    protected String tableName = null;
    protected Class<?> cls = null;
    protected List<OrderBy> orderBys = new ArrayList<OrderBy>();
    protected Expr condition = Expr.dummy();
    protected String _tableSymbol = null;
    protected int _limit = -1;
    protected int _offset = -1;
    protected Map<Integer, Object> indexParameters = new HashMap<Integer, Object>();
    protected Map<String, Object> mapParameters = new HashMap<String, Object>();

    public String getTableSymbol() {
        if (_tableSymbol == null) {
            _tableSymbol = StringUtil.randomString(5);
        }
        return _tableSymbol;
    }

    /**
     * @param tableName maybe `User` or `User user`(then you can use user.name='abc' in expr)
     */
    public Query(String tableName) {
        this.tableName = tableName;
    }

    public Query(Class<?> cls) {
        this.cls = cls;
        this.tableName = cls.getSimpleName(); // 这里使用类名而不是@Table(name=...)是因为HQL使用的是类名
    }

    public Query<M> limit(int limit) {
        _limit = limit;
        return this;
    }

    public Query<M> setMaxRows(int limit) {
        return limit(limit);
    }

    public Query<M> offset(int offset) {
        this._offset = offset;
        return this;
    }

    public Query<M> where(Expr expr) {
        this.condition = expr;
        return this;
    }

    public Query<M> clone() {
        Query<M> query = new Query<M>(tableName);
        query._limit = this._limit;
        query._offset = this._offset;
        query._tableSymbol = this._tableSymbol;
        query.condition = this.condition;
        query.orderBys = this.orderBys; // clone it
        query.indexParameters = this.indexParameters; // clone it
        query.mapParameters = this.mapParameters; // clone it
        return query;
    }

    public Query<M> setParameter(String key, Object value) {
        this.mapParameters.put(key, value);
        return this;
    }

    public Query<M> setParameter(int index, Object value) {
        this.indexParameters.put(index, value);
        return this;
    }

    public Query<M> eq(String name, Object value) {
        this.condition = this.condition.eq(name, value);
        return this;
    }

    public Query<M> ne(String name, Object value) {
        this.condition = this.condition.ne(name, value);
        return this;
    }

    public Query<M> gt(String name, Object value) {
        this.condition = this.condition.gt(name, value);
        return this;
    }

    public Query<M> ge(String name, Object value) {
        this.condition = this.condition.ge(name, value);
        return this;
    }

    public Query<M> like(String name, Object value) {
        this.condition = this.condition.like(name, value);
        return this;
    }

    public Query<M> lt(String name, Object value) {
        this.condition = this.condition.lt(name, value);
        return this;
    }

    public Query<M> le(String name, Object value) {
        this.condition = this.condition.le(name, value);
        return this;
    }

    public Query<M> or(Expr expr1, Expr expr2) {
        this.condition = this.condition.and(expr1.or(expr2));
        return this;
    }

    public Query<M> and(Expr expr) {
        this.condition = this.condition.and(expr);
        return this;
    }

    public Query<M> orderBy(String sort) {
        return orderBy(sort, true);
    }

    public Query<M> orderBy(String sort, boolean asc) {
        this.orderBys.add(new OrderBy(sort, asc));
        return this;
    }

    public Query<M> asc(String sort) {
        return orderBy(sort, true);
    }

    public Query<M> desc(String sort) {
        return orderBy(sort, false);
    }

    public String getOrderByString() {
        final Query _this = this;
        List<String> orderByStrs = ListUtil.map(this.orderBys, new Function<OrderBy, String>() {
            @Override
            public String apply(OrderBy orderBy) {
                return orderBy != null ? orderBy.toOrderByString(_this) : null;
            }
        });
        return StringUtil.join(orderByStrs, ",");
    }

    public QueryInfo toQuery() {
        String queryStr = "from " + tableName + " ";
        QueryInfo exprQuery = this.condition != null ? this.condition.toQueryString(this) : null;
        if (exprQuery != null) {
            queryStr += " where " + exprQuery.getQueryString();
        }
        if (this.orderBys.size() > 0) {
            queryStr += " order by " + this.getOrderByString();
        }
        ParameterBindings bindings = new ParameterBindings();
        if (exprQuery != null) {
            bindings = exprQuery.getParameterBindings();
        }
        QueryExtras extras = new QueryExtras();
        if (this._limit >= 0) {
            extras.setMax(this._limit);
        }
        if (this._offset >= 0) {
            extras.setOffset(this._offset);
        }
        return new QueryInfo(queryStr, bindings, extras);
    }

    public long count(Session session) {
        if (this.cls == null) {
            throw new RuntimeException("you need pass a model class");
        }
        return count(session, this.cls);
    }

    public long count() {
        return count(Model.getSession());
    }

    public long count(Session session, Class<?> model) {
        // 不能直接getSingleResult,因为在分布式mysql集群中,count语句可能返回多个值
        List result = getTypedQuery(session, Long.class, new Function<String, String>() {
            @Override
            public String apply(String s) {
                return "select count(*) " + s;
            }
        }).getResultList();
        return (Long) ListUtil.reduce(result, 0L, new Function2() {
            @Override
            public Object apply(Object o1, Object o2) {
                if (o1 == null && o2 == null) {
                    return 0L;
                }
                if (o1 == null) {
                    return o2;
                }
                return (Long) o1 + (Long) o2;
            }
        });
    }

    public long count(Class<?> model) {
        return count(Model.getSession(), model);
    }

    public List<M> all() {
        return all(Model.getSession());
    }

    public List<M> all(Session session) {
        if (this.cls == null) {
            throw new RuntimeException("you need pass a model class");
        }
        return all(session, this.cls);
    }

    public IWrappedQuery getTypedQuery(Session session, Class<?> model) {
        return getTypedQuery(session, model, null);
    }

    public IWrappedQuery getTypedQuery(Class<?> model) {
        return getTypedQuery(Model.getSession(), model);
    }

    public IWrappedQuery getTypedQuery(Class<?> model, Function<String, String> queryWrapper) {
        return getTypedQuery(Model.getSession(), model, queryWrapper);
    }

    /**
     * @param model        要查询的model
     * @param queryWrapper 用来对HQL进行二次处理,处理后再用来执行
     */
    public IWrappedQuery getTypedQuery(Session session, Class<?> model, Function<String, String> queryWrapper) {
        QueryInfo query = this.toQuery();
        String queryString = query.getQueryString();
        if (queryWrapper != null) {
            queryString = queryWrapper.apply(queryString);
        }
        IWrappedQuery typedQuery = session.createQuery(model, queryString);
//        TypedQuery typedQuery = session.getEntityManager().createQuery(queryString, model);
        ParameterBindings bindings = query.getParameterBindings();
        if (bindings != null) {
            for (int i = 0; i < bindings.getIndexBindings().size(); ++i) {
                typedQuery.setParameter(i + 1, bindings.getIndexBindings().get(i));
            }
            for (String key : bindings.getMapBindings().keySet()) {
                typedQuery.setParameter(key, bindings.getMapBindings().get(key));
            }
        }
        QueryExtras extras = query.getExtras();
        if (extras != null) {
            if (extras.getMax() >= 0) {
                typedQuery.setMaxResults(extras.getMax());
            }
            if (extras.getOffset() >= 0) {
                typedQuery.setFirstResult(extras.getOffset());
            }
        }
        return typedQuery;
    }

    public List<M> findList() {
        return findList(Model.getSession());
    }

    public List<M> findList(Session session) {
        return all(session);
    }

    public List<M> all(Class<?> model) {
        return all(Model.getSession(), model);
    }

    public List<M> all(Session session, Class<?> model) {
        return getTypedQuery(session, model).getResultList();
    }

    public M first() {
        return first(Model.getSession());
    }

    public M first(Session session) {
        if (this.cls == null) {
            throw new RuntimeException("you need pass a model class");
        }
        return first(session, this.cls);
    }

    public M findUnique() {
        return findUnique(Model.getSession());
    }

    public M findUnique(Session session) {
        return first(session);
    }

    public M first(Class<?> model) {
        return first(Model.getSession(), model);
    }

    public M first(Session session, Class<?> model) {
        return (M) ListUtil.first(getTypedQuery(session, model).setMaxResults(1).getResultList());
    }

    public M single(Class<?> model) {
        return single(Model.getSession(), model);
    }

    public M single(Session session, Class<?> model) {
        return (M) getTypedQuery(session, model).setMaxResults(1).getSingleResult();
    }
}
