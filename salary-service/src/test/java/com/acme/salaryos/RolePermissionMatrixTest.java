package com.acme.salaryos;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CLAUDE.md §7 / salary-management-backend.md §4.1: every controller method carries
 * {@code @PreAuthorize} matching the RBAC table exactly — a method with none also fails the
 * build, since silence is not a permission. Walks the real stub controllers (P2.4); as P4+
 * replaces each stub with a real implementation, its entry here just keeps being the guard.
 */
class RolePermissionMatrixTest {

	private static final List<Class<?>> CONTROLLERS = List.of(
			com.acme.salaryos.employee.web.EmployeeController.class,
			com.acme.salaryos.band.web.BandController.class,
			com.acme.salaryos.change.web.ChangeController.class,
			com.acme.salaryos.analytics.web.AnalyticsController.class,
			com.acme.salaryos.reference.web.ReferenceController.class,
			com.acme.salaryos.auth.web.UserAdminController.class,
			com.acme.salaryos.audit.AuditController.class,
			com.acme.salaryos.compensation.web.ProjectionAdminController.class,
			com.acme.salaryos.fx.FxRateController.class);

	private static final Set<String> MAPPING_ANNOTATIONS = Set.of(
			"GetMapping", "PostMapping", "PatchMapping", "PutMapping", "DeleteMapping", "RequestMapping");

	private static final Pattern ROLE_LITERAL = Pattern.compile("'([A-Z_]+)'");

	private static final Set<String> HR_ADMIN_ONLY = Set.of("HR_ADMIN");
	private static final Set<String> ADMIN_AND_MANAGER = Set.of("HR_ADMIN", "HR_MANAGER");
	private static final Set<String> ADMIN_MANAGER_ANALYST = Set.of("HR_ADMIN", "HR_MANAGER", "COMP_ANALYST");
	private static final Set<String> ADMIN_AND_AUDITOR = Set.of("HR_ADMIN", "AUDITOR");
	private static final Set<String> VIEW_PAY = Set.of("HR_ADMIN", "HR_MANAGER", "COMP_ANALYST", "AUDITOR");

	/** "ClassSimpleName#methodName" -> the exact role set CLAUDE.md §7 grants that capability. */
	private static final Map<String, Set<String>> EXPECTED_ROLES = expectedRoles();

	@Test
	void everyEndpointMethodHasAPreAuthorizeAnnotation() {
		List<String> missing = new ArrayList<>();
		for (Class<?> controller : CONTROLLERS) {
			for (Method method : endpointMethods(controller)) {
				if (method.getAnnotation(PreAuthorize.class) == null) {
					missing.add(key(controller, method));
				}
			}
		}
		assertThat(missing).as("endpoint method(s) missing @PreAuthorize").isEmpty();
	}

	@Test
	void everyEndpointGrantsExactlyTheRolesClaudeMdSection7Documents() {
		Map<String, String> mismatches = new HashMap<>();
		for (Class<?> controller : CONTROLLERS) {
			for (Method method : endpointMethods(controller)) {
				String key = key(controller, method);
				PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);
				assertThat(preAuthorize).as(key + " missing @PreAuthorize").isNotNull();

				Set<String> expected = EXPECTED_ROLES.get(key);
				assertThat(expected).as("no expected-roles entry in this test for " + key + " — add one").isNotNull();

				Set<String> actual = rolesIn(preAuthorize.value());
				if (!actual.equals(expected)) {
					mismatches.put(key, "expected " + expected + " but annotation grants " + actual);
				}
			}
		}
		assertThat(mismatches).as("role mismatches against CLAUDE.md §7").isEmpty();
	}

	private static Map<String, Set<String>> expectedRoles() {
		Map<String, Set<String>> map = new HashMap<>();

		for (String method : List.of("list", "get", "compensationHistory", "compensationAsAt", "peers", "export")) {
			map.put("EmployeeController#" + method, VIEW_PAY);
		}
		for (String method : List.of("create", "update", "terminate")) {
			map.put("EmployeeController#" + method, ADMIN_AND_MANAGER);
		}

		map.put("BandController#list", VIEW_PAY);
		map.put("BandController#create", ADMIN_AND_MANAGER);
		map.put("BandController#update", ADMIN_AND_MANAGER);
		map.put("BandController#importCsv", HR_ADMIN_ONLY);

		for (String method : List.of("list", "propose", "updateDraft", "submit", "discardDraft")) {
			map.put("ChangeController#" + method, ADMIN_MANAGER_ANALYST);
		}
		map.put("ChangeController#approve", ADMIN_AND_MANAGER);
		map.put("ChangeController#reject", ADMIN_AND_MANAGER);
		map.put("ChangeController#applyDue", HR_ADMIN_ONLY);
		map.put("ChangeController#bulkUpload", HR_ADMIN_ONLY);

		for (String method : List.of(
				"payrollCost", "outOfBand", "compaRatioDistribution", "payGap", "increaseCycle", "headcount")) {
			map.put("AnalyticsController#" + method, ADMIN_MANAGER_ANALYST);
		}

		for (String method : List.of(
				"departments", "locations", "countries", "jobFamilies", "jobLevels", "currencies")) {
			map.put("ReferenceController#" + method, VIEW_PAY);
		}

		for (String method : List.of("list", "create", "update", "issueResetToken")) {
			map.put("UserAdminController#" + method, HR_ADMIN_ONLY);
		}

		map.put("AuditController#search", ADMIN_AND_AUDITOR);
		map.put("ProjectionAdminController#rebuild", HR_ADMIN_ONLY);

		map.put("FxRateController#list", VIEW_PAY);
		map.put("FxRateController#add", ADMIN_AND_MANAGER);

		return map;
	}

	private String key(Class<?> controller, Method method) {
		return controller.getSimpleName() + "#" + method.getName();
	}

	private Set<String> rolesIn(String preAuthorizeExpression) {
		Set<String> roles = new TreeSet<>();
		Matcher matcher = ROLE_LITERAL.matcher(preAuthorizeExpression);
		while (matcher.find()) {
			roles.add(matcher.group(1));
		}
		return roles;
	}

	private List<Method> endpointMethods(Class<?> controller) {
		List<Method> methods = new ArrayList<>();
		for (Method method : controller.getDeclaredMethods()) {
			if (hasMappingAnnotation(method)) {
				methods.add(method);
			}
		}
		return methods;
	}

	private boolean hasMappingAnnotation(Method method) {
		for (Annotation annotation : method.getAnnotations()) {
			if (MAPPING_ANNOTATIONS.contains(annotation.annotationType().getSimpleName())) {
				return true;
			}
		}
		return false;
	}

}
