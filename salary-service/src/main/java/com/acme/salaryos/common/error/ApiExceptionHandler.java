package com.acme.salaryos.common.error;

import com.acme.salaryos.auth.service.CannotChangeOwnRoleException;
import com.acme.salaryos.auth.service.LastActiveHrAdminException;
import com.acme.salaryos.band.service.BandAlreadyExistsException;
import com.acme.salaryos.band.service.BandBackdatedException;
import com.acme.salaryos.band.service.BandNotOpenException;
import com.acme.salaryos.band.service.BandOrderingException;
import com.acme.salaryos.change.service.ChangeCurrencyMismatchException;
import com.acme.salaryos.change.service.ChangeNoteRequiredException;
import com.acme.salaryos.change.service.ChangeNotDraftException;
import com.acme.salaryos.change.service.ChangeNotDueException;
import com.acme.salaryos.change.service.ChangeNotPendingException;
import com.acme.salaryos.change.service.NoCurrentCompensationException;
import com.acme.salaryos.change.service.OpenChangeAlreadyExistsException;
import com.acme.salaryos.change.service.SelfApprovalException;
import com.acme.salaryos.common.paging.InvalidCursorException;
import com.acme.salaryos.compensation.effective.BackdatedBeforeOpenPeriodException;
import com.acme.salaryos.compensation.effective.CorrectionOutsideOriginalPeriodException;
import com.acme.salaryos.compensation.effective.MissingCorrectionNoteException;
import com.acme.salaryos.compensation.effective.MissingFxRateException;
import com.acme.salaryos.employee.service.EmployeeAlreadyHasCompensationException;
import com.acme.salaryos.fx.service.FxRateAlreadyExistsException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.BadCredentialsException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.validation.FieldError;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.NoSuchElementException;
import java.util.stream.Collectors;

/**
 * Every failure is an RFC 7807 {@code ProblemDetail} with a {@code detail} written for a human
 * (salary-management-backend.md §8). Grows one handler per domain exception as later steps add
 * them.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

	/**
	 * Bean-validation failures on a {@code @Valid @RequestBody} (P10.5, found in QA).
	 *
	 * <p>Without this handler the failure was <b>reported to the client as 401 "Authentication
	 * required"</b>, on every validated endpoint in the service. Spring's default resolver sets a
	 * bare 400 and the container then forwards to {@code /error}; that forward is a fresh ERROR
	 * dispatch, the session-cookie filter is a {@code OncePerRequestFilter} and does not run
	 * again, so the security chain sees an empty {@code SecurityContext} on a request matched by
	 * {@code anyRequest().authenticated()} and the entry point overwrites the 400 with a 401. The
	 * symptom is the worst kind: a user who typed a bad value is told they are signed out, and the
	 * validation message they needed never reaches them. Handling the exception here produces a
	 * normal response and no ERROR dispatch happens at all.
	 *
	 * <p>The field messages are the ones written on the DTO's constraints, so the client can show
	 * the same sentence the API author intended rather than a generic "invalid request".
	 */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ProblemDetail handleValidationFailure(MethodArgumentNotValidException exception) {
		String detail = exception.getBindingResult().getFieldErrors().stream()
				.map(error -> error.getField() + ": " + error.getDefaultMessage())
				.collect(Collectors.joining(" "));
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(
				HttpStatus.BAD_REQUEST, detail.isBlank() ? "The request was not valid." : detail);
		problem.setProperty("errors", exception.getBindingResult().getFieldErrors().stream()
				.collect(Collectors.toMap(
						FieldError::getField,
						error -> error.getDefaultMessage() == null ? "Invalid." : error.getDefaultMessage(),
						(first, second) -> first)));
		return problem;
	}

	/** Same reasoning as above for a {@code @Validated} parameter that is not a request body. */
	@ExceptionHandler(ConstraintViolationException.class)
	public ProblemDetail handleConstraintViolation(ConstraintViolationException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
	}

	/*
	 * The handlers below close a gap found in the QA sweep: §8 of the backend doc says *every*
	 * failure is an RFC 7807 ProblemDetail with a detail written for a human, because the UI shows
	 * that string directly. Spring's own MVC exceptions were falling through to the container's
	 * default error body -- `{"timestamp":...,"error":"Bad Request","path":...}` -- which has no
	 * `detail` at all, so `ApiError.problem` on the client was undefined and the user got generic
	 * fallback copy on every malformed request. The status codes were right; the contract was not.
	 */

	/** A path variable or query parameter that could not be parsed: a bad UUID, `limit=abc`, an
	 * unknown enum value, an unparseable date. Names the parameter, since the client cannot
	 * otherwise tell which of several it got wrong. */
	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
		Class<?> required = exception.getRequiredType();
		String expected = required == null ? "a different type"
				: required.isEnum()
						? "one of " + String.join(", ", java.util.Arrays.stream(required.getEnumConstants())
								.map(String::valueOf).toList())
						: switch (required.getSimpleName()) {
							case "UUID" -> "an id";
							case "LocalDate" -> "a date as YYYY-MM-DD";
							case "Integer", "int", "Long", "long" -> "a whole number";
							case "BigDecimal" -> "a number";
							default -> "a " + required.getSimpleName();
						};
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
				"'" + exception.getName() + "' should be " + expected + ".");
	}

	/** A required query parameter that was not sent at all. */
	@ExceptionHandler(MissingServletRequestParameterException.class)
	public ProblemDetail handleMissingParameter(MissingServletRequestParameterException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
				"'" + exception.getParameterName() + "' is required.");
	}

	/** A body that is not parseable JSON at all — malformed, empty, or the wrong content type. */
	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ProblemDetail handleUnreadableBody(HttpMessageNotReadableException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
				"The request body could not be read as JSON.");
	}

	/**
	 * An upload with no file on it. This was a <b>500</b> before the QA sweep, on every importer in
	 * the service — bands, employees, changes, and market data — because {@code MultipartException}
	 * had no handler. Sending a form with an empty file input is a user mistake, not a server fault,
	 * and 500 tells the user to contact someone rather than to attach a file.
	 */
	@ExceptionHandler({MultipartException.class, MissingServletRequestPartException.class})
	public ProblemDetail handleMissingUpload(Exception exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
				"Attach a CSV file to upload.");
	}

	/** The method is wrong for this path — a POST where the resource only answers GET. */
	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	public ProblemDetail handleMethodNotSupported(HttpRequestMethodNotSupportedException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.METHOD_NOT_ALLOWED,
				exception.getMethod() + " is not supported here.");
	}

	/** No handler at all — a mistyped URL. 404 with a body the client can read like any other. */
	@ExceptionHandler(NoHandlerFoundException.class)
	public ProblemDetail handleNoHandler(NoHandlerFoundException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Not found.");
	}

	@ExceptionHandler(BadCredentialsException.class)
	public ProblemDetail handleBadCredentials(BadCredentialsException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, exception.getMessage());
	}

	@ExceptionHandler(InvalidCursorException.class)
	public ProblemDetail handleInvalidCursor(InvalidCursorException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
	}

	@ExceptionHandler(NoSuchElementException.class)
	public ProblemDetail handleNotFound() {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Not found.");
	}

	@ExceptionHandler(CannotChangeOwnRoleException.class)
	public ProblemDetail handleCannotChangeOwnRole(CannotChangeOwnRoleException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
	}

	@ExceptionHandler(LastActiveHrAdminException.class)
	public ProblemDetail handleLastActiveHrAdmin(LastActiveHrAdminException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
	}

	@ExceptionHandler(BackdatedBeforeOpenPeriodException.class)
	public ProblemDetail handleBackdated(BackdatedBeforeOpenPeriodException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
	}

	@ExceptionHandler(MissingFxRateException.class)
	public ProblemDetail handleMissingFxRate(MissingFxRateException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, exception.getMessage());
	}

	@ExceptionHandler(FxRateAlreadyExistsException.class)
	public ProblemDetail handleFxRateAlreadyExists(FxRateAlreadyExistsException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
	}

	@ExceptionHandler(MissingCorrectionNoteException.class)
	public ProblemDetail handleMissingCorrectionNote(MissingCorrectionNoteException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
	}

	@ExceptionHandler(CorrectionOutsideOriginalPeriodException.class)
	public ProblemDetail handleCorrectionOutsideOriginalPeriod(CorrectionOutsideOriginalPeriodException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
	}

	@ExceptionHandler(EmployeeAlreadyHasCompensationException.class)
	public ProblemDetail handleEmployeeAlreadyHasCompensation(EmployeeAlreadyHasCompensationException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
	}

	@ExceptionHandler(BandAlreadyExistsException.class)
	public ProblemDetail handleBandAlreadyExists(BandAlreadyExistsException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
	}

	@ExceptionHandler(BandNotOpenException.class)
	public ProblemDetail handleBandNotOpen(BandNotOpenException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
	}

	@ExceptionHandler(BandBackdatedException.class)
	public ProblemDetail handleBandBackdated(BandBackdatedException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
	}

	@ExceptionHandler(BandOrderingException.class)
	public ProblemDetail handleBandOrdering(BandOrderingException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
	}

	/** FR-5.6: exposes the open change's id so the UI can link straight to it, not just say "one exists somewhere". */
	@ExceptionHandler(OpenChangeAlreadyExistsException.class)
	public ProblemDetail handleOpenChangeAlreadyExists(OpenChangeAlreadyExistsException exception) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
		problem.setProperty("openChangeId", exception.getOpenChangeId());
		return problem;
	}

	@ExceptionHandler(SelfApprovalException.class)
	public ProblemDetail handleSelfApproval(SelfApprovalException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, exception.getMessage());
	}

	@ExceptionHandler(ChangeNoteRequiredException.class)
	public ProblemDetail handleChangeNoteRequired(ChangeNoteRequiredException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
	}

	@ExceptionHandler(ChangeNotDraftException.class)
	public ProblemDetail handleChangeNotDraft(ChangeNotDraftException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
	}

	@ExceptionHandler(ChangeNotPendingException.class)
	public ProblemDetail handleChangeNotPending(ChangeNotPendingException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
	}

	@ExceptionHandler(ChangeNotDueException.class)
	public ProblemDetail handleChangeNotDue(ChangeNotDueException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
	}

	@ExceptionHandler(NoCurrentCompensationException.class)
	public ProblemDetail handleNoCurrentCompensation(NoCurrentCompensationException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
	}

	@ExceptionHandler(ChangeCurrencyMismatchException.class)
	public ProblemDetail handleChangeCurrencyMismatch(ChangeCurrencyMismatchException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
	}

	/**
	 * The {@code comp_no_overlap} exclusion constraint is the backstop against a race the service
	 * layer's own open-period check can still lose (backend doc §3, rule 2) — never swallowed,
	 * always surfaced as a 409 rather than a raw 500.
	 */
	@ExceptionHandler(DataIntegrityViolationException.class)
	public ProblemDetail handleDataIntegrityViolation(DataIntegrityViolationException exception) {
		String message = String.valueOf(exception.getMostSpecificCause().getMessage());
		if (message.contains("comp_no_overlap")) {
			return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
					"This employee already has an overlapping pay period. Choose a non-overlapping effective date.");
		}
		if (message.contains("one_open_change_per_employee")) {
			return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
					"A change for this employee is already awaiting approval.");
		}
		throw exception;
	}

}
