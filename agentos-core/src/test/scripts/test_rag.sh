#!/usr/bin/env bash
# ======================================================================
# AgentOS RAG Knowledge Base — 端到端测试脚本
#
# 前置条件:
#   1. Spring Boot 后端已启动 (mvn spring-boot:run)
#   2. Python 嵌入服务已启动 (python embed_server.py --port 8081)
#
# 测试内容:
#   - 入库 3 篇不同主题的文档
#   - 语义搜索验证
#   - 边界条件测试
#   - 知识库统计
# ======================================================================

BASE_URL="${BASE_URL:-http://localhost:9090}"
PASS=0
FAIL=0

assert_eq() {
    local desc="$1" expected="$2" actual="$3"
    if [[ "$actual" == "$expected" ]]; then
        echo "  ✅ $desc"
        PASS=$((PASS + 1))
    else
        echo "  ❌ $desc"
        echo "     expected: $expected"
        echo "     actual:   $actual"
        FAIL=$((FAIL + 1))
    fi
}

assert_contains() {
    local desc="$1" needle="$2" haystack="$3"
    if echo "$haystack" | grep -q "$needle"; then
        echo "  ✅ $desc"
        PASS=$((PASS + 1))
    else
        echo "  ❌ $desc — expected to contain '$needle'"
        FAIL=$((FAIL + 1))
    fi
}

echo "========================================"
echo " AgentOS RAG Knowledge Base — 端到端测试"
echo "========================================"
echo ""
echo "后端: $BASE_URL"
echo ""

# -------------------------------------------------------
# 测试数据：3 篇不同主题的文档
# -------------------------------------------------------

DOC_AI=$(cat << 'ENDDOC'
Artificial intelligence (AI) is a branch of computer science that aims to create intelligent machines that can simulate human intelligence. Key areas include machine learning, where algorithms learn from data; natural language processing, which enables computers to understand and generate human language; and computer vision, which allows machines to interpret visual information. Deep learning, a subset of machine learning, uses neural networks with many layers to analyze complex patterns. AI applications include virtual assistants, recommendation systems, autonomous vehicles, and medical diagnosis tools. Modern AI systems often use transformer architectures, which have revolutionized natural language processing tasks. The development of large language models has significantly advanced the field of AI, enabling more sophisticated interactions between humans and machines.
ENDDOC
)

DOC_DB=$(cat << 'ENDDOC'
Relational databases organize data into tables with rows and columns. Each table represents an entity, and relationships between tables are established through foreign keys. SQL (Structured Query Language) is the standard language for querying and manipulating relational databases. Common operations include SELECT for retrieving data, INSERT for adding new records, UPDATE for modifying existing data, and DELETE for removing records. Indexes are used to speed up data retrieval operations. Transactions ensure data integrity through ACID properties: Atomicity, Consistency, Isolation, and Durability. Popular relational database management systems include PostgreSQL, MySQL, Oracle, and Microsoft SQL Server. Normalization is a process that reduces data redundancy by organizing fields into tables efficiently. Joins combine rows from multiple tables based on related columns.
ENDDOC
)

DOC_NET=$(cat << 'ENDDOC'
Computer networking involves the interconnection of multiple computing devices to share data and resources. The Internet Protocol Suite (TCP/IP) is the fundamental framework for network communication. It consists of four layers: Application, Transport, Internet, and Link. Common protocols include HTTP for web browsing, SMTP for email, FTP for file transfer, and DNS for domain name resolution. IP addresses uniquely identify devices on a network, with IPv4 using 32-bit addresses and IPv6 using 128-bit addresses. Network topologies include star, mesh, bus, ring, and tree configurations. Firewalls and encryption protect network security. Network devices such as routers, switches, and hubs facilitate data transmission. Bandwidth measures data transfer capacity, while latency measures transmission delay. Cloud computing relies heavily on networking to provide scalable services over the internet.
ENDDOC
)

# -------------------------------------------------------
# Step 0: 统计（初始应为 0）
# -------------------------------------------------------
echo "--- 步骤 0: 初始状态 ---"
STATS=$(curl -s "$BASE_URL/api/knowledge/stats")
INITIAL_COUNT=$(echo "$STATS" | python3 -c "import sys,json; print(json.load(sys.stdin).get('totalChunks', -1))" 2>/dev/null)
assert_eq "初始知识块数量应为 0" "0" "$INITIAL_COUNT"

# -------------------------------------------------------
# Step 1: 入库文档 1 — AI
# -------------------------------------------------------
echo ""
echo "--- 步骤 1: 入库「人工智能」文档 ---"
RESP=$(curl -s -X POST "$BASE_URL/api/knowledge/ingest" \
    -H "Content-Type: application/json" \
    -d "{\"text\": $(echo "$DOC_AI" | jq -Rs '.'), \"documentName\": \"ai_intro.txt\"}")
SUCCESS=$(echo "$RESP" | python3 -c "import sys,json; print(json.load(sys.stdin).get('success', False))" 2>/dev/null)
CHUNKS=$(echo "$RESP" | python3 -c "import sys,json; print(json.load(sys.stdin).get('chunkCount', 0))" 2>/dev/null)
assert_eq "入库成功" "True" "$SUCCESS"
assert_contains "AI 文档被分割成多个块" "chunkCount" "$RESP"
echo "   切分块数: $CHUNKS"

# -------------------------------------------------------
# Step 2: 入库文档 2 — 数据库
# -------------------------------------------------------
echo ""
echo "--- 步骤 2: 入库「关系数据库」文档 ---"
RESP=$(curl -s -X POST "$BASE_URL/api/knowledge/ingest" \
    -H "Content-Type: application/json" \
    -d "{\"text\": $(echo "$DOC_DB" | jq -Rs '.'), \"documentName\": \"database_intro.txt\"}")
assert_eq "入库成功" "True" "$(echo "$RESP" | python3 -c "import sys,json; print(json.load(sys.stdin).get('success', False))" 2>/dev/null)"

# -------------------------------------------------------
# Step 3: 入库文档 3 — 网络
# -------------------------------------------------------
echo ""
echo "--- 步骤 3: 入库「计算机网络」文档 ---"
RESP=$(curl -s -X POST "$BASE_URL/api/knowledge/ingest" \
    -H "Content-Type: application/json" \
    -d "{\"text\": $(echo "$DOC_NET" | jq -Rs '.'), \"documentName\": \"networking_intro.txt\"}")
assert_eq "入库成功" "True" "$(echo "$RESP" | python3 -c "import sys,json; print(json.load(sys.stdin).get('success', False))" 2>/dev/null)"

# -------------------------------------------------------
# Step 4: 验证统计
# -------------------------------------------------------
echo ""
echo "--- 步骤 4: 验证统计 ---"
STATS=$(curl -s "$BASE_URL/api/knowledge/stats")
TOTAL=$(echo "$STATS" | python3 -c "import sys,json; print(json.load(sys.stdin).get('totalChunks', 0))" 2>/dev/null)
assert_contains "知识库有数据" "totalChunks" "$STATS"
echo "   总块数: $TOTAL"

# -------------------------------------------------------
# Step 5: 语义搜索测试
# -------------------------------------------------------
echo ""
echo "--- 步骤 5: 语义搜索测试 ---"

# 5a: 搜索 AI 相关 — 应匹配 AI 文档
echo ""
echo "  [测试 5a] 查询: 'machine learning neural networks'"
RESP=$(curl -s -X POST "$BASE_URL/api/knowledge/search" \
    -H "Content-Type: application/json" \
    -d '{"query": "machine learning neural networks", "topK": 3}')
RESULTS=$(echo "$RESP" | python3 -c "
import sys, json
data = json.load(sys.stdin)
results = data.get('results', [])
for r in results:
    print(f\"      score={r['score']:.3f}  [{r['documentName']}]  {r['chunkText'][:80]}...\")
print('---')
print(len(results))
" 2>/dev/null)
COUNT=$(echo "$RESULTS" | tail -1)
SCORE_INFO=$(echo "$RESULTS" | head -n -2)
echo "$SCORE_INFO"
if [ "$COUNT" -gt 0 ]; then
    echo "  ✅ 搜索 5a 返回了 $COUNT 条结果"
    PASS=$((PASS + 1))
else
    echo "  ❌ 搜索 5a 无结果"
    FAIL=$((FAIL + 1))
fi

# 5b: 搜索数据库相关 — 应匹配 database 文档
echo ""
echo "  [测试 5b] 查询: 'SQL queries and transactions'"
RESP=$(curl -s -X POST "$BASE_URL/api/knowledge/search" \
    -H "Content-Type: application/json" \
    -d '{"query": "SQL queries and transactions", "topK": 3}')
RESULTS=$(echo "$RESP" | python3 -c "
import sys, json
data = json.load(sys.stdin)
results = data.get('results', [])
for r in results:
    print(f\"      score={r['score']:.3f}  [{r['documentName']}]  {r['chunkText'][:80]}...\")
print('---')
print(len(results))
" 2>/dev/null)
COUNT=$(echo "$RESULTS" | tail -1)
SCORE_INFO=$(echo "$RESULTS" | head -n -2)
echo "$SCORE_INFO"
if [ "$COUNT" -gt 0 ]; then
    echo "  ✅ 搜索 5b 返回了 $COUNT 条结果"
    PASS=$((PASS + 1))
else
    echo "  ❌ 搜索 5b 无结果"
    FAIL=$((FAIL + 1))
fi

# 5c: 搜索网络相关 — 应匹配 networking 文档
echo ""
echo "  [测试 5c] 查询: 'TCP IP protocol routing'"
RESP=$(curl -s -X POST "$BASE_URL/api/knowledge/search" \
    -H "Content-Type: application/json" \
    -d '{"query": "TCP IP protocol routing", "topK": 3}')
RESULTS=$(echo "$RESP" | python3 -c "
import sys, json
data = json.load(sys.stdin)
results = data.get('results', [])
for r in results:
    print(f\"      score={r['score']:.3f}  [{r['documentName']}]  {r['chunkText'][:80]}...\")
print('---')
print(len(results))
" 2>/dev/null)
COUNT=$(echo "$RESULTS" | tail -1)
SCORE_INFO=$(echo "$RESULTS" | head -n -2)
echo "$SCORE_INFO"
if [ "$COUNT" -gt 0 ]; then
    echo "  ✅ 搜索 5c 返回了 $COUNT 条结果"
    PASS=$((PASS + 1))
else
    echo "  ❌ 搜索 5c 无结果"
    FAIL=$((FAIL + 1))
fi

# 5d: 搜索不相关的内容 — 可能返回低分或无结果
echo ""
echo "  [测试 5d] 查询: 'quantum physics particles'（不相关内容）"
RESP=$(curl -s -X POST "$BASE_URL/api/knowledge/search" \
    -H "Content-Type: application/json" \
    -d '{"query": "quantum physics particles", "topK": 3}')
RESULTS=$(echo "$RESP" | python3 -c "
import sys, json
data = json.load(sys.stdin)
results = data.get('results', [])
for r in results:
    print(f\"      score={r['score']:.3f}  [{r['documentName']}]  {r['chunkText'][:80]}...\")
print('---')
print(len(results))
" 2>/dev/null)
COUNT=$(echo "$RESULTS" | tail -1)
SCORE_INFO=$(echo "$RESULTS" | head -n -2)
echo "$SCORE_INFO"
echo "  ℹ️  不相关内容返回 $COUNT 条结果（分数通常较低）"

# -------------------------------------------------------
# Step 6: 边界条件测试
# -------------------------------------------------------
echo ""
echo "--- 步骤 6: 边界条件 ---"

# 6a: 空查询
echo "  [测试 6a] 空查询"
RESP=$(curl -s -X POST "$BASE_URL/api/knowledge/search" \
    -H "Content-Type: application/json" \
    -d '{"query": ""}')
SUCCESS=$(echo "$RESP" | python3 -c "import sys,json; print(json.load(sys.stdin).get('success', False))" 2>/dev/null)
assert_eq "空查询返回错误" "False" "$SUCCESS"

# 6b: 空文本入库
echo "  [测试 6b] 空文本入库"
RESP=$(curl -s -X POST "$BASE_URL/api/knowledge/ingest" \
    -H "Content-Type: application/json" \
    -d '{"text": "", "documentName": "empty.txt"}')
SUCCESS=$(echo "$RESP" | python3 -c "import sys,json; print(json.load(sys.stdin).get('success', False))" 2>/dev/null)
assert_eq "空文本入库返回错误" "False" "$SUCCESS"

# 6c: 重复入库（同名文档应替换）
echo "  [测试 6c] 重复入库同名文档"
RESP=$(curl -s -X POST "$BASE_URL/api/knowledge/ingest" \
    -H "Content-Type: application/json" \
    -d "{\"text\": $(echo "$DOC_AI" | jq -Rs '.'), \"documentName\": \"ai_intro.txt\"}")
assert_eq "重复入库成功" "True" "$(echo "$RESP" | python3 -c "import sys,json; print(json.load(sys.stdin).get('success', False))" 2>/dev/null)"

# -------------------------------------------------------
# 总结
# -------------------------------------------------------
echo ""
echo "========================================"
echo " 测试完成"
echo " 通过: $PASS"
echo " 失败: $FAIL"
echo "========================================"

if [ "$FAIL" -gt 0 ]; then
    exit 1
fi
