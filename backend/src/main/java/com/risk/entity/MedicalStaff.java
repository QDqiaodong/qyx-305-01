package com.risk.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 随队医护人员。
 */
@Entity
@Table(name = "medical_staff")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MedicalStaff {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 医护姓名 */
    @Column(name = "staff_name", nullable = false, length = 100)
    private String staffName;

    /** 执业/资质编号 */
    @Column(name = "certificate_no", length = 50)
    private String certificateNo;

    /** 联系电话 */
    @Column(name = "phone", length = 30)
    private String phone;

    /** 是否具备儿科资质：1 是 / 0 否 */
    @Column(name = "pediatric_qualified", nullable = false)
    @Builder.Default
    private Integer pediatricQualified = 0;

    /** 职称/岗位说明 */
    @Column(name = "title", length = 100)
    private String title;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
