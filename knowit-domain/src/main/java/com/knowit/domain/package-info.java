/**
 * 纯领域层。
 *
 * <p>唯一的纪律（由 ArchUnit 强制）：本包不得 import 任何第三方类， 也不得 import 其他 knowit-* 业务模块。 领域模型用 record 与 enum
 * 表达，不用 Lombok——零注解处理、IDE 可完整跳转。
 */
package com.knowit.domain;
