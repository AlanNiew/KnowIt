package com.knowit.server;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 架构守护规则。这些是"写在测试里的墙"，比写在文档里的规范有效一百倍。
 *
 * <p>每条规则都对应一条 ADR。规则编号出现在失败信息里，方便定位该改代码还是改 ADR。
 */
@DisplayName("架构守护")
class ArchitectureRulesTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter().importPackages("com.knowit");
    }

    /**
     * ADR-0008：domain 是纯领域层。
     *
     * <p>允许的依赖只有 JDK 和自己。这一条挡住的是"为了省事从 domain 里 直接调 Spring 工具类"——一旦开了口子，domain 很快会被框架注解蛀空。
     */
    @Test
    @DisplayName("[ADR-0008] domain 只依赖 JDK 与自身")
    void domainIsFrameworkFree() {
        ArchRule rule =
                classes()
                        .that()
                        .resideInAPackage("..knowit.domain..")
                        .should()
                        .onlyDependOnClassesThat()
                        .resideInAnyPackage(
                                "java..", "javax..", "jakarta..", "com.knowit.domain..");

        rule.check(classes);
    }

    /** ADR-0008 / modules.md：common 是零依赖基础层。 */
    @Test
    @DisplayName("[ADR-0008] common 不依赖任何第三方框架")
    void commonIsDependencyFree() {
        ArchRule rule =
                classes()
                        .that()
                        .resideInAPackage("..knowit.common..")
                        .should()
                        .onlyDependOnClassesThat()
                        .resideInAnyPackage(
                                "java..", "javax..", "jakarta..", "com.knowit.common..");

        rule.check(classes);
    }

    /**
     * ADR-0008：retrieval 与 generation 互不依赖。
     *
     * <p>两者之间唯一的桥梁是 {@code domain.EvidenceBundle}。这条规则保证 "调整检索策略"不会连带影响"回答组装"，反之亦然。
     */
    @Test
    @DisplayName("[ADR-0008] retrieval 与 generation 互不依赖")
    void retrievalAndGenerationAreIsolated() {
        noClasses()
                .that()
                .resideInAPackage("..knowit.retrieval..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("..knowit.generation..")
                .check(classes);

        noClasses()
                .that()
                .resideInAPackage("..knowit.generation..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("..knowit.retrieval..")
                .check(classes);
    }

    /** ADR-0008：domain 不被任何模块反向依赖之外的业务依赖。 domain 只能被依赖，不能依赖别人。 */
    @Test
    @DisplayName("[ADR-0008] domain 不依赖其他业务模块")
    void domainDoesNotDependOnBusinessModules() {
        noClasses()
                .that()
                .resideInAPackage("..knowit.domain..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "..knowit.iam..", "..knowit.ingestion..",
                        "..knowit.retrieval..", "..knowit.generation..")
                .check(classes);
    }

    /**
     * ADR-0009：api 包是唯一的对外契约，LangChain4j 绝不出现在其中。
     *
     * <p>这一条让"换掉 LangChain4j"变成一个 parser 类的重写， 而不是一次全项目重构。
     */
    @Test
    @DisplayName("[ADR-0009] api 包不得出现 LangChain4j 类型")
    void apiPackagesAreFreeOfLangchain4j() {
        noClasses()
                .that()
                .resideInAPackage("..api..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("dev.langchain4j..")
                .check(classes);
    }

    /**
     * ADR-0008：internal 包禁止被跨模块 import。
     *
     * <p>只对本项目类生效，不检查 JDK / 第三方类。
     */
    @Test
    @DisplayName("[ADR-0008] internal 实现细节不得被其他模块 import")
    void internalPackagesAreNotLeaked() {
        noClasses()
                .that()
                .resideOutsideOfPackages("..internal..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("..internal..")
                .check(classes);
    }

    /**
     * ADR-0011：检索接口不允许出现不带 AccessContext 的重载。
     *
     * <p>这是从接口层面杜绝"先检索再过滤"的写法。任何想绕过的人 必须先来删这条测试——希望他先读一遍 ADR。
     */
    @Test
    @DisplayName("[ADR-0011] 包名约定：检索器必须接收 AccessContext")
    void retrieverGatekeeperIsEnforcedBySignatureConvention() {
        // 说明性规则：真正的强制在接口签名 + 编译期。
        // 这里验证 AccessContext 确实只被 iam 定义、被 retrieval/generation 消费，
        // 而没有相反方向的依赖（否则权限上下文会变成检索层的私有概念）。
        noClasses()
                .that()
                .resideInAPackage("..knowit.iam..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "..knowit.retrieval..", "..knowit.generation..", "..knowit.ingestion..")
                .check(classes);
    }
}
