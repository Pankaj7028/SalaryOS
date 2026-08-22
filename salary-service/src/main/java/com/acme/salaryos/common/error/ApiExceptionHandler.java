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
import com.acme.salaryos.fx.service.FxRateAlreadyExistsException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.NoSuchElementException;

/**
 * Every failure is an RFC 7807 {@code ProblemDetail} with a {@code detail} written for a human
 * (salary-management-backend.md §8). Grows one handler per domain exception as later steps add
 * them.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

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
