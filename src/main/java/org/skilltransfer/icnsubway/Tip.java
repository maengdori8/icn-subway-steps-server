package org.skilltransfer.icnsubway;

import jakarta.persistence.*;
import java.time.Instant;

/** 한 단계에 붙는 팁. owner는 올린 사용자이며 서버가 토큰으로 정한다. */
@Entity
@Table(name = "tip", indexes = {
        @Index(name = "ix_tip_route_step", columnList = "route_code, step_no"),
        @Index(name = "ix_tip_owner", columnList = "owner_id")})
public class Tip {
    public enum Status { VERIFIED, OUTDATED }

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "tip_seq")
    @SequenceGenerator(name = "tip_seq", sequenceName = "tip_seq", initialValue = 9, allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "route_code", nullable = false)
    private Route route;

    @Column(name = "step_no", nullable = false) private int stepNo;
    // API 규칙은 코드 포인트 80·150자. H2는 UTF-16 단위로 세므로 열은 두 배로 잡는다.
    @Column(nullable = false, length = 160) private String title;
    @Column(nullable = false, length = 160) private String location;
    @Column(length = 300) private String note;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private Status status;
    @Column(name = "verified_at", nullable = false) private Instant verifiedAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private AppUser owner;

    @Column(nullable = false) private boolean example;

    protected Tip() {}

    /** 사용자가 올린 새 팁: 서버가 VERIFIED, 지금 시각, example=false로 정한다. */
    public Tip(Route route, int stepNo, String title, String location, String note, AppUser owner, Instant now) {
        this.route = route;
        this.stepNo = stepNo;
        this.title = title;
        this.location = location;
        this.note = note;
        this.owner = owner;
        this.status = Status.VERIFIED;
        this.verifiedAt = now;
        this.createdAt = now;
        this.example = false;
    }

    /** 작성자가 상태를 정할 때마다 확인 시각을 새로 기록한다. */
    public void changeStatus(Status status, Instant now) {
        this.status = status;
        this.verifiedAt = now;
    }

    public Long getId() { return id; }
    public Route getRoute() { return route; }
    public int getStepNo() { return stepNo; }
    public String getTitle() { return title; }
    public String getLocation() { return location; }
    public String getNote() { return note; }
    public Status getStatus() { return status; }
    public Instant getVerifiedAt() { return verifiedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public AppUser getOwner() { return owner; }
    public boolean isExample() { return example; }
}
