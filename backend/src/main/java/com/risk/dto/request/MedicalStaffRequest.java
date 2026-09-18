package com.risk.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MedicalStaffRequest {

    @NotBlank(message = "医护姓名不能为空")
    private String staffName;

    private String certificateNo;

    private String phone;

    /** 是否具备儿科资质：true 是 / false 否 */
    private Boolean pediatricQualified;

    private String title;
}
