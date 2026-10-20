package org.skilltransfer.icnsubway;

import jakarta.persistence.*;

/** 경로의 한 단계. 문구는 시안 v03 초안이며 현장 확인 전이다(docs/data-model.md). */
@Entity
@Table(name = "route_step", uniqueConstraints = @UniqueConstraint(columnNames = {"route_code", "step_no"}))
public class RouteStep {
    public enum Role { HERE, FIND }
    public enum Icon { PLANE, ESCALATOR, TRAIN }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "route_code", nullable = false)
    private Route route;

    @Column(name = "step_no", nullable = false) private int stepNo;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 8) private Role role;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private Icon icon;
    @Column(name = "photo_key", length = 40) private String photoKey;
    @Column(name = "sign_ko", nullable = false, length = 80) private String signKo;
    @Column(name = "sign_en", nullable = false, length = 80) private String signEn;
    @Column(name = "title_en", nullable = false, length = 120) private String titleEn;
    @Column(name = "title_ko", nullable = false, length = 120) private String titleKo;
    /** 줄바꿈으로 이은 안내 줄. API는 배열로 준다. */
    @Column(name = "instructions_en", nullable = false, length = 1000) private String instructionsEn;
    @Column(name = "instructions_ko", nullable = false, length = 1000) private String instructionsKo;
    /** 현장 확인 전에는 null. */
    @Column(name = "elevator_en", length = 200) private String elevatorEn;
    @Column(name = "elevator_ko", length = 200) private String elevatorKo;

    protected RouteStep() {}

    public int getStepNo() { return stepNo; }
    public Role getRole() { return role; }
    public Icon getIcon() { return icon; }
    public String getPhotoKey() { return photoKey; }
    public String getSignKo() { return signKo; }
    public String getSignEn() { return signEn; }
    public String getTitleEn() { return titleEn; }
    public String getTitleKo() { return titleKo; }
    public String getInstructionsEn() { return instructionsEn; }
    public String getInstructionsKo() { return instructionsKo; }
    public String getElevatorEn() { return elevatorEn; }
    public String getElevatorKo() { return elevatorKo; }
}
