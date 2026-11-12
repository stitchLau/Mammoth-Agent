# ADR-0004 Artifact Storage Spike（一次性）

对比两种不可变原始资料（Artifact）存储方案，使用同一组合成文件（6 个文件、3 份唯一内容，含 5 MB 大文件与 0 字节文件）：

- `filesystem`：本地内容寻址目录 `<work>/artifacts/<sha[0..2]>/<sha[2..4]>/<sha>`，写入用临时文件 + 原子移动，`dos:readonly` 置只读。
- `sqlite`：SQLite 表 `artifact(sha256 UNIQUE, bytes BLOB)`，用 `INSERT OR IGNORE` 去重，`VACUUM INTO` 备份。

测量：写/读耗时、去重次数、逻辑/实际字节、哈希校验、只读是否生效、备份大小/耗时、删除→恢复。

## 运行

```powershell
mvn -B -q -f spike/adr-0004-artifact/pom.xml -DskipTests package
$cp = "spike\adr-0004-artifact\target\classes;spike\adr-0004-artifact\target\lib\*"
java -cp $cp com.mammoth.artifactspike.ArtifactSpike filesystem `
  spike/adr-0004-artifact/target/work spike/adr-0004-artifact/target/result-filesystem.json
java -cp $cp com.mammoth.artifactspike.ArtifactSpike sqlite `
  spike/adr-0004-artifact/target/work spike/adr-0004-artifact/target/result-sqlite.json
```

## 说明

- 一次性验证代码，不是产品基线；结果写入 `docs/spikes/S5-ARTIFACT-STORAGE-SPIKE.md`。
- 不读写真实客户资料；全部内容为随机/合成数据。
- 对象存储（S3/MinIO）本 Spike 未实现，作为服务端阶段候选在 ADR 中记录。
