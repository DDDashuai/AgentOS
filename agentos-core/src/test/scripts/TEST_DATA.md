# =====================================================================
# AgentOS RAG 知识库 — 测试数据和预期结果
# =====================================================================
#
# 3 篇测试文档 + 6 组查询用例
# =====================================================================

# =====================================================================
# 测试文档 1: 人工智能 (ai_intro.txt)
# 关键词: machine learning, neural networks, deep learning, NLP,
#          transformer, large language models, computer vision
# =====================================================================
Artificial intelligence (AI) is a branch of computer science that aims to
create intelligent machines that can simulate human intelligence. Key areas
include machine learning, where algorithms learn from data; natural language
processing, which enables computers to understand and generate human language;
and computer vision, which allows machines to interpret visual information.
Deep learning, a subset of machine learning, uses neural networks with many
layers to analyze complex patterns. AI applications include virtual assistants,
recommendation systems, autonomous vehicles, and medical diagnosis tools.
Modern AI systems often use transformer architectures, which have
revolutionized natural language processing tasks. The development of large
language models has significantly advanced the field of AI, enabling more
sophisticated interactions between humans and machines.

# =====================================================================
# 测试文档 2: 关系数据库 (database_intro.txt)
# 关键词: SQL, SELECT, transactions, ACID, PostgreSQL, indexes, joins,
#          normalization, foreign keys
# =====================================================================
Relational databases organize data into tables with rows and columns. Each
table represents an entity, and relationships between tables are established
through foreign keys. SQL (Structured Query Language) is the standard language
for querying and manipulating relational databases. Common operations include
SELECT for retrieving data, INSERT for adding new records, UPDATE for modifying
existing data, and DELETE for removing records. Indexes are used to speed up
data retrieval operations. Transactions ensure data integrity through ACID
properties: Atomicity, Consistency, Isolation, and Durability. Popular
relational database management systems include PostgreSQL, MySQL, Oracle, and
Microsoft SQL Server. Normalization is a process that reduces data redundancy
by organizing fields into tables efficiently. Joins combine rows from multiple
tables based on related columns.

# =====================================================================
# 测试文档 3: 计算机网络 (networking_intro.txt)
# 关键词: TCP/IP, HTTP, DNS, IP addresses, routers, firewalls, cloud
#          computing, bandwidth, latency
# =====================================================================
Computer networking involves the interconnection of multiple computing devices
to share data and resources. The Internet Protocol Suite (TCP/IP) is the
fundamental framework for network communication. It consists of four layers:
Application, Transport, Internet, and Link. Common protocols include HTTP for
web browsing, SMTP for email, FTP for file transfer, and DNS for domain name
resolution. IP addresses uniquely identify devices on a network, with IPv4
using 32-bit addresses and IPv6 using 128-bit addresses. Network topologies
include star, mesh, bus, ring, and tree configurations. Firewalls and
encryption protect network security. Network devices such as routers, switches,
and hubs facilitate data transmission. Bandwidth measures data transfer
capacity, while latency measures transmission delay. Cloud computing relies
heavily on networking to provide scalable services over the internet.


# =====================================================================
# 测试用例
# =====================================================================
#
# 用例 1: 精确匹配
#   query: "machine learning neural networks deep learning"
#   预期: 匹配 ai_intro.txt，score > 0.7，结果中应包含 "neural networks"
#
# 用例 2: 关键词匹配
#   query: "SQL transactions ACID"
#   预期: 匹配 database_intro.txt，score > 0.6，结果中应包含 "ACID"
#
# 用例 3: 同义词/语义匹配
#   query: "computer networks protocols"
#   预期: 匹配 networking_intro.txt，得分最高
#
# 用例 4: 无关查询（低分或空）
#   query: "quantum physics particles"
#   预期: 可能返回低分结果或空列表，最高分通常 < 0.5
#
# 用例 5: 边界 — 空查询
#   query: ""
#   预期: 400 Bad Request, success = false
#
# 用例 6: 边界 — 空文档
#   text: ""
#   预期: 400 Bad Request, success = false
#


# =====================================================================
# 预期结果对照表
# =====================================================================
#
# +-------+---------------------------+------------------+--------------+
# | 用例  | 查询                      | 最佳匹配文档     | 最低分数    |
# +-------+---------------------------+------------------+--------------+
# | 1     | machine learning neural   | ai_intro.txt     | > 0.70      |
# | 2     | SQL transactions ACID     | database_intro.txt| > 0.60     |
# | 3     | TCP IP routing protocols  | networking_intro.txt| > 0.60   |
# | 4     | quantum physics           | (任意或无)       | < 0.50      |
# | 5     | (空串)                   | -                | 400 错误    |
# | 6     | (空文档)                 | -                | 400 错误    |
# +-------+---------------------------+------------------+--------------+
