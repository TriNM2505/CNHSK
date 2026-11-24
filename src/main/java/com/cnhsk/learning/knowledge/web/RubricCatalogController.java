package com.cnhsk.learning.knowledge.web;

import java.util.List;
import java.util.Map;

import com.cnhsk.learning.knowledge.entity.LearningTask;
import com.cnhsk.learning.knowledge.repository.ErrorCodeRepository;
import com.cnhsk.learning.knowledge.repository.LearningTaskRepository;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read-only catalogues of the competency tree used by speaking/writing tasks and rubric grading. */
@RestController
@Transactional(readOnly = true)
public class RubricCatalogController {

    private final LearningTaskRepository tasks;
    private final ErrorCodeRepository errorCodes;

    public RubricCatalogController(LearningTaskRepository tasks, ErrorCodeRepository errorCodes) {
        this.tasks = tasks;
        this.errorCodes = errorCodes;
    }

    @Schema(name = "LearningTaskView")
    public record TaskView(@Schema(type = "integer", format = "int64") long id, String code,
            @Schema(allowableValues = {"SPEAKING", "WRITING"}) String kind, int hskLevel, String nlKid,
            String titleVi, String promptVi, String promptZh, Integer minChars, Integer maxSeconds) { }

    @Schema(name = "ErrorCodeView")
    public record ErrorCodeView(String code, String kid, int severity, String nameVi, String example, String fixHint) { }

    /** Public so visitors can see what each level asks for; submitting still needs an account. */
    @GetMapping("/api/public/learning/tasks")
    @Operation(operationId = "listLearningTasks", tags = "speaking")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Nhiệm vụ nói/viết theo cấp HSK"),
            @ApiResponse(responseCode = "422", description = "VALIDATION_FAILED")})
    public List<TaskView> tasks(@RequestParam String kind, @RequestParam int hskLevel) {
        LearningTask.Kind parsed;
        try {
            parsed = LearningTask.Kind.valueOf(kind);
        } catch (IllegalArgumentException exception) {
            throw invalid();
        }
        if (hskLevel < 1 || hskLevel > 9) throw invalid();
        return tasks.findActive(parsed, (short) hskLevel).stream().map(task -> new TaskView(task.getId(),
                task.getCode(), task.getKind().name(), task.getHskLevel(), task.getNlKid().getCode(), task.getTitleVi(),
                task.getPromptVi(), task.getPromptZh(),
                task.getMinChars() == null ? null : task.getMinChars().intValue(),
                task.getMaxSeconds() == null ? null : task.getMaxSeconds().intValue())).toList();
    }

    @GetMapping("/api/teacher/error-codes")
    @Operation(operationId = "listErrorCodes", tags = "teacher-grading")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Mã lỗi giáo viên gắn khi chấm (phụ lục D)")})
    public List<ErrorCodeView> errorCodes() {
        return errorCodes.findAll(Sort.by("code")).stream().map(code -> new ErrorCodeView(code.getCode(),
                code.getKid() == null ? null : code.getKid().getCode(), code.getSeverity(), code.getNameVi(),
                code.getExample(), code.getFixHint())).toList();
    }

    private static ApiException invalid() {
        return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY,
                "Loại nhiệm vụ hoặc cấp HSK không hợp lệ.", Map.of());
    }
}
