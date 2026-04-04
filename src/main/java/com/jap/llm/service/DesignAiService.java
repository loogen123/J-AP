package com.jap.llm.service;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface DesignAiService {

    @SystemMessage("""
        你是 J-AP 的首席架构师，专注于生成结构化的工程文档。
        
        你的使命是在编写任何代码之前创建全面的设计文档。
        
        ## 输出规则
        - 只返回有效的 Markdown 内容
        - 使用 Mermaid 图表进行可视化
        - 内容具体且可执行
        - 包含正常流程和错误处理
        - 关注"做什么"和"为什么"，而不是"怎么做"（实现细节稍后处理）
        - 所有内容必须使用中文
        """)
    @UserMessage("""
        请为以下需求生成产品需求文档（PRD）。
        
        ## 用户需求
        {{requirement}}
        
        ## 包名
        {{packageName}}
        
        ## 数据库类型
        {{databaseType}}
        
        请生成一份完整的中文 PRD 文档，包含以下章节：
        
        1. **概述** - 系统简介
        2. **用例矩阵** - 所有用例表格，包含：
           - 用例编号
           - 参与者
           - 描述
           - 前置条件
           - 后置条件
        3. **逆向用例** - 错误场景和边界情况：
           - 无效输入处理
           - 资源未找到
           - 权限拒绝
           - 并发访问问题
        4. **业务状态机** - Mermaid stateDiagram 展示：
           - 实体生命周期
           - 状态转换
           - 触发器和守卫条件
        5. **API 契约预览** - RESTful 端点摘要
        
        只返回 Markdown 内容，不要用代码块包裹。
        """)
    String generatePRD(
        @V("requirement") String requirement,
        @V("packageName") String packageName,
        @V("databaseType") String databaseType
    );

    @SystemMessage("""
        你是 J-AP 的系统架构师，专注于技术架构文档。
        
        你的使命是创建详细的架构文档来指导实现。
        
        ## 输出规则
        - 只返回有效的 Markdown 内容
        - 所有图表使用 Mermaid
        - 组件交互要具体明确
        - 定义清晰的契约和接口
        - 所有内容必须使用中文
        """)
    @UserMessage("""
        请根据 PRD 和需求生成架构设计文档。
        
        ## 用户需求
        {{requirement}}
        
        ## PRD 摘要
        {{prdSummary}}
        
        ## 包名
        {{packageName}}
        
        ## 技术栈
        - JDK 25 结构化并发
        - Spring Boot 3.4 虚拟线程
        - LangChain4j 1.0.0
        - MySQL 9.x / Redis 7.4
        
        请生成一份完整的中文架构文档，包含：
        
        1. **系统概述** - 高层架构说明
        2. **组件图** - Mermaid flowchart 展示：
           - 表现层（Controllers）
           - 业务层（Services）
           - 数据层（Repositories）
           - 外部集成
        3. **时序图** - Mermaid sequenceDiagram 展示：
           - 主流程正常路径
           - 错误处理流程
        4. **API 契约** - 详细的 REST API 规范：
           - 端点路径
           - HTTP 方法
           - 请求/响应 DTO
           - 状态码
        5. **数据模型** - 实体关系 Mermaid erDiagram
        6. **中间件栈** - 缓存、验证、异常处理
        
        只返回 Markdown 内容，不要用代码块包裹。
        """)
    String generateArchitecture(
        @V("requirement") String requirement,
        @V("prdSummary") String prdSummary,
        @V("packageName") String packageName
    );

    @SystemMessage("""
        你是 J-AP 的 UI 原型生成器，创建功能性 HTML 原型。
        
        你的使命是创建一个可工作的静态 HTML 原型来演示核心功能。
        
        ## 输出规则
        - 只返回有效的 HTML 内容（完整文档）
        - 使用 Tailwind CSS CDN 进行样式设计
        - 包含可工作的 JavaScript 交互
        - 视觉美观且功能完整
        - 除 CDN 资源外无外部依赖
        - 界面文字使用中文
        """)
    @UserMessage("""
        请为以下系统创建一个功能性 HTML 原型。
        
        ## 用户需求
        {{requirement}}
        
        ## API 端点
        {{apiEndpoints}}
        
        ## 包名
        {{packageName}}
        
        请生成一个完整的 HTML 原型，包含：
        
        1. **头部** - 应用标题和导航
        2. **主内容区** - 核心功能：
           - 创建实体的输入表单
           - 显示实体的列表/表格
           - 操作按钮（创建、读取、更新、删除）
        3. **状态区** - 反馈消息
        4. **JavaScript** - 
           - 模拟数据处理
           - 表单验证
           - CRUD 操作模拟
           - 动态 UI 更新
        
        样式要求：
        - 使用 Tailwind CSS 的现代简洁设计
        - 响应式布局
        - 清晰的视觉层次
        - 交互反馈（悬停状态、加载指示器）
        
        只返回完整的 HTML 文档，不要用 markdown 包裹。
        """)
    String generatePrototype(
        @V("requirement") String requirement,
        @V("apiEndpoints") String apiEndpoints,
        @V("packageName") String packageName
    );
}
