package org.skilltransfer.icnsubway.domain;

import jakarta.persistence.*;

/** 경로(EXPRESS·LOCAL·T2). 단계 수는 route_step 개수로 계산한다. */
@Entity
@Table(name = "route")
public class Route {
    @Id
    @Column(length = 16)
    private String code;

    @Column(name = "name_en", nullable = false, length = 80) private String nameEn;
    @Column(name = "name_ko", nullable = false, length = 80) private String nameKo;
    @Column(name = "summary_en", nullable = false, length = 120) private String summaryEn;
    @Column(name = "summary_ko", nullable = false, length = 120) private String summaryKo;
    @Column(nullable = false) private boolean available;
    @Column(name = "sort_order", nullable = false) private int sortOrder;

    protected Route() {}

    public String getCode() { return code; }
    public String getNameEn() { return nameEn; }
    public String getNameKo() { return nameKo; }
    public String getSummaryEn() { return summaryEn; }
    public String getSummaryKo() { return summaryKo; }
    public boolean isAvailable() { return available; }
}
