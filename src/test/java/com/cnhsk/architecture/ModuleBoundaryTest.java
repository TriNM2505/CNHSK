package com.cnhsk.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Kiem tra ranh gioi giua ba module auth / learning / community.
 *
 * VI SAO CAN FILE NAY:
 * Kien truc Modular Monolith khong co tien trinh rieng, cung khong co tai khoan
 * database rieng de ep ranh gioi module. Day la thu DUY NHAT chan viec goi cheo.
 * Bo file nay thi sau vai thang du an thanh mot khoi ron - dung thu ma viec chia
 * module dinh tranh.
 *
 * TEST DO THI SUA CODE, KHONG SUA LUAT.
 * Can goi cheo module ma chua co ham? Them ham vao interface trong goi <module>.api.
 *
 * VE allowEmptyShould(true):
 * Du an moi khoi tao, cac goi module con rong nen chua co lop nao de kiem.
 * Mac dinh ArchUnit bao loi khi rule khong kiem duoc lop nao - de bat loi go sai
 * ten goi. O day biet chac la goi rong that, nen tat rieng tung luat.
 * Khi ba module da co code, CO THE BO cac dong allowEmptyShould de lay lai
 * lop bao ve chong go sai ten goi.
 */
@AnalyzeClasses(
        packages = "com.cnhsk",
        importOptions = ImportOption.DoNotIncludeTests.class
)
class ModuleBoundaryTest {

    // ===== Module community khong duoc cham vao ruot cua module khac =====

    @ArchTest
    static final ArchRule community_chi_duoc_goi_auth_qua_api =
            noClasses().that().resideInAPackage("..community..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            "..auth.service..",
                            "..auth.repository..",
                            "..auth.entity..",
                            "..auth.controller..")
                    .because("community phai goi qua interface trong auth.api (UserLookup), "
                            + "de sau nay tach service chi can doi phan cai dat interface")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule community_chi_duoc_goi_learning_qua_api =
            noClasses().that().resideInAPackage("..community..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            "..learning.payment..",
                            "..learning.library..",
                            "..learning.exam..",
                            "..learning.srs..",
                            "..learning.ai..",
                            "..learning.grading..")
                    .because("community phai goi qua interface trong learning.api "
                            + "(MasteryUpdater, ContentLookup)")
                    .allowEmptyShould(true);

    // ===== Module learning khong duoc cham vao ruot cua module khac =====

    @ArchTest
    static final ArchRule learning_chi_duoc_goi_auth_qua_api =
            noClasses().that().resideInAPackage("..learning..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            "..auth.service..",
                            "..auth.repository..",
                            "..auth.entity..",
                            "..auth.controller..")
                    .because("learning phai goi qua interface trong auth.api (UserLookup)")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule learning_khong_duoc_biet_den_community =
            noClasses().that().resideInAPackage("..learning..")
                    .should().dependOnClassesThat().resideInAPackage("..community..")
                    .because("luong du lieu di mot chieu: community goi learning, "
                            + "khong nguoc lai. Diem game cong mastery la community goi "
                            + "MasteryUpdater, khong phai learning goi community")
                    .allowEmptyShould(true);

    // ===== Module auth phai doc lap hoan toan =====

    @ArchTest
    static final ArchRule auth_khong_duoc_biet_den_hai_module_kia =
            noClasses().that().resideInAPackage("..auth..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..learning..", "..community..")
                    .because("auth la module nen tang, phai tach ra duoc de dang nhat. "
                            + "Auth biet den module khac thi khong bao gio tach duoc")
                    .allowEmptyShould(true);

    // ===== Module shared khong duoc phu thuoc nguoc =====

    @ArchTest
    static final ArchRule shared_khong_duoc_phu_thuoc_module_nghiep_vu =
            noClasses().that().resideInAPackage("..shared..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..auth..", "..learning..", "..community..")
                    .because("shared la tang duoi cung - ai cung dung duoc shared, "
                            + "nhung shared khong duoc dung nguoc len")
                    .allowEmptyShould(true);

    // ===== Giu ba tang Controller -> Service -> Repository =====

    @ArchTest
    static final ArchRule controller_khong_duoc_goi_thang_repository =
            noClasses().that().resideInAPackage("..controller..")
                    .should().dependOnClassesThat().resideInAPackage("..repository..")
                    .because("Controller goi Service, Service goi Repository. "
                            + "Goi tat lam logic nghiep vu roi vao Controller")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule repository_khong_duoc_goi_nguoc_len_service =
            noClasses().that().resideInAPackage("..repository..")
                    .should().dependOnClassesThat().resideInAPackage("..service..")
                    .because("Repository la tang duoi cung, khong duoc goi nguoc len")
                    .allowEmptyShould(true);
}
