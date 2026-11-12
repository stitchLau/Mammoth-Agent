# M1 演示脚本

一键演示 M1 端到端闭环（合成数据，不涉及真实客户资料）：

1. 健康检查；
2. 创建两个本地用户（owner / reviewer）；
3. 创建客户与项目，reviewer 加入项目；
4. 上传原始资料（multipart，内容寻址去重）；
5. 创建知识文档（必须引用资料）；
6. 提交审核 → reviewer 审核通过（创建者自审被拒）；
7. 全文检索（仅 verified、仅本项目）；
8. 读取文档与引用；
9. 负向校验：非成员检索 403、草稿/未提交状态非法跳转。

## 运行

```powershell
# 1) 构建并启动 Domain Service（使用独立演示数据根）
mvn -B -ntp -f services/domain/pom.xml package
$env:MAMMOTH_DATA_ROOT = Join-Path $env:TEMP "mammoth-demo"
java -jar services/domain/target/mammoth-domain-service.jar --server.port=8080

# 2) 另开终端执行演示
node scripts/demo-m1.mjs http://127.0.0.1:8080
```

输出为 JSON：`{"passed":N,"failed":M,"steps":[{"name","ok","detail"}...]}`。
任一步失败会以 `ok=false` 标出，退出码非 0。

## 边界

- 演示仅覆盖 M1 已实现路径（T1–T6/T8/T9）；认证仍是临时身份头；
- 不接触生产环境、真实凭据或客户数据；
- 每次运行创建新的合成项目（带随机后缀），可重复执行。
