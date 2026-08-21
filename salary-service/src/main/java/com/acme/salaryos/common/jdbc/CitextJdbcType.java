package com.acme.salaryos.common.jdbc;

import org.hibernate.type.descriptor.ValueBinder;
import org.hibernate.type.descriptor.ValueExtractor;
import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.JavaType;
import org.hibernate.type.descriptor.jdbc.BasicBinder;
import org.hibernate.type.descriptor.jdbc.BasicExtractor;
import org.hibernate.type.descriptor.jdbc.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

/**
 * Binds/extracts a plain {@code String} against Postgres's {@code citext} — the driver reports
 * it as {@code Types.OTHER}, and Hibernate has no built-in mapping for it (unlike {@code inet}).
 * Hibernate's generic OTHER handling for a String falls back to a binary bind, which citext
 * accepts but silently corrupts (reads back as a bytea hex literal) — this binds/extracts via
 * plain {@code setString}/{@code getString} instead, so the type code still matches what schema
 * validation expects from the catalog.
 */
public class CitextJdbcType implements JdbcType {

	public static final CitextJdbcType INSTANCE = new CitextJdbcType();

	@Override
	public int getJdbcTypeCode() {
		return Types.OTHER;
	}

	@Override
	public <X> ValueBinder<X> getBinder(JavaType<X> javaType) {
		return new BasicBinder<>(javaType, this) {
			@Override
			protected void doBind(PreparedStatement st, X value, int index, WrapperOptions options) throws SQLException {
				st.setString(index, javaType.unwrap(value, String.class, options));
			}

			@Override
			protected void doBind(CallableStatement st, X value, String name, WrapperOptions options) throws SQLException {
				st.setString(name, javaType.unwrap(value, String.class, options));
			}
		};
	}

	@Override
	public <X> ValueExtractor<X> getExtractor(JavaType<X> javaType) {
		return new BasicExtractor<>(javaType, this) {
			@Override
			protected X doExtract(ResultSet rs, int paramIndex, WrapperOptions options) throws SQLException {
				return javaType.wrap(rs.getString(paramIndex), options);
			}

			@Override
			protected X doExtract(CallableStatement statement, int index, WrapperOptions options) throws SQLException {
				return javaType.wrap(statement.getString(index), options);
			}

			@Override
			protected X doExtract(CallableStatement statement, String name, WrapperOptions options) throws SQLException {
				return javaType.wrap(statement.getString(name), options);
			}
		};
	}

}
