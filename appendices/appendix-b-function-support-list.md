# 附录 B：函数支持列表

本附录列出 Gluten 在 Velox 和 ClickHouse 后端支持的所有函数，包括标量函数、聚合函数和窗口函数。

## B.1 Velox 后端支持的函数

### B.1.1 数学函数

| 函数 | 说明 | 示例 |
|------|------|------|
| `abs(x)` | 绝对值 | `abs(-5)` → 5 |
| `acos(x)` | 反余弦 | `acos(0.5)` → 1.047... |
| `asin(x)` | 反正弦 | `asin(0.5)` → 0.523... |
| `atan(x)` | 反正切 | `atan(1)` → 0.785... |
| `atan2(y, x)` | 两参数反正切 | `atan2(1, 1)` → 0.785... |
| `ceil(x)` | 向上取整 | `ceil(3.14)` → 4 |
| `cos(x)` | 余弦 | `cos(0)` → 1.0 |
| `cosh(x)` | 双曲余弦 | `cosh(0)` → 1.0 |
| `exp(x)` | 指数函数 | `exp(1)` → 2.718... |
| `floor(x)` | 向下取整 | `floor(3.14)` → 3 |
| `ln(x)` | 自然对数 | `ln(2.718)` → 1.0 |
| `log10(x)` | 以10为底对数 | `log10(100)` → 2.0 |
| `log2(x)` | 以2为底对数 | `log2(8)` → 3.0 |
| `mod(n, m)` | 取模 | `mod(10, 3)` → 1 |
| `pi()` | 圆周率 | `pi()` → 3.14159... |
| `pow(x, y)` | 幂运算 | `pow(2, 3)` → 8.0 |
| `round(x, d)` | 四舍五入 | `round(3.14159, 2)` → 3.14 |
| `sign(x)` | 符号函数 | `sign(-5)` → -1 |
| `sin(x)` | 正弦 | `sin(0)` → 0.0 |
| `sinh(x)` | 双曲正弦 | `sinh(0)` → 0.0 |
| `sqrt(x)` | 平方根 | `sqrt(16)` → 4.0 |
| `tan(x)` | 正切 | `tan(0)` → 0.0 |
| `tanh(x)` | 双曲正切 | `tanh(0)` → 0.0 |
| `degrees(x)` | 弧度转角度 | `degrees(pi())` → 180.0 |
| `radians(x)` | 角度转弧度 | `radians(180)` → 3.14159... |

### B.1.2 字符串函数

| 函数 | 说明 | 示例 |
|------|------|------|
| `concat(str1, str2, ...)` | 连接字符串 | `concat('Hello', ' ', 'World')` → 'Hello World' |
| `concat_ws(sep, str1, str2, ...)` | 用分隔符连接 | `concat_ws(',', 'A', 'B')` → 'A,B' |
| `length(str)` | 字符串长度 | `length('Hello')` → 5 |
| `lower(str)` | 转小写 | `lower('HELLO')` → 'hello' |
| `upper(str)` | 转大写 | `upper('hello')` → 'HELLO' |
| `ltrim(str)` | 左侧去空格 | `ltrim('  Hello')` → 'Hello' |
| `rtrim(str)` | 右侧去空格 | `rtrim('Hello  ')` → 'Hello' |
| `trim(str)` | 两侧去空格 | `trim('  Hello  ')` → 'Hello' |
| `substring(str, pos, len)` | 子串 | `substring('Hello', 1, 3)` → 'Hel' |
| `substr(str, pos, len)` | 子串（别名） | `substr('Hello', 1, 3)` → 'Hel' |
| `replace(str, from, to)` | 替换 | `replace('Hello', 'l', 'L')` → 'HeLLo' |
| `split(str, pattern)` | 分割字符串 | `split('A,B,C', ',')` → ['A', 'B', 'C'] |
| `strpos(str, substr)` | 查找子串位置 | `strpos('Hello', 'l')` → 3 |
| `reverse(str)` | 反转字符串 | `reverse('Hello')` → 'olleH' |
| `repeat(str, n)` | 重复字符串 | `repeat('Ha', 3)` → 'HaHaHa' |
| `lpad(str, len, pad)` | 左侧填充 | `lpad('5', 3, '0')` → '005' |
| `rpad(str, len, pad)` | 右侧填充 | `rpad('5', 3, '0')` → '500' |
| `ascii(str)` | ASCII 码 | `ascii('A')` → 65 |
| `chr(n)` | ASCII 转字符 | `chr(65)` → 'A' |
| `md5(str)` | MD5 哈希 | `md5('Hello')` → '8b1a...' |
| `sha1(str)` | SHA1 哈希 | `sha1('Hello')` → 'f7ff...' |
| `sha256(str)` | SHA256 哈希 | `sha256('Hello')` → '185f...' |

### B.1.3 正则表达式函数

| 函数 | 说明 | 示例 |
|------|------|------|
| `regexp_extract(str, pattern, idx)` | 提取匹配组 | `regexp_extract('100-200', '(\d+)-(\d+)', 1)` → '100' |
| `regexp_replace(str, pattern, rep)` | 正则替换 | `regexp_replace('100-200', '\d+', 'X')` → 'X-X' |
| `regexp_like(str, pattern)` | 正则匹配 | `regexp_like('abc123', '.*\d+')` → true |
| `rlike(str, pattern)` | 正则匹配（别名） | `rlike('abc', '[a-z]+')` → true |

### B.1.4 日期时间函数

| 函数 | 说明 | 示例 |
|------|------|------|
| `current_date()` | 当前日期 | `current_date()` → '2024-01-15' |
| `current_timestamp()` | 当前时间戳 | `current_timestamp()` → '2024-01-15 10:30:00' |
| `now()` | 当前时间戳（别名） | `now()` → '2024-01-15 10:30:00' |
| `year(date)` | 年份 | `year('2024-01-15')` → 2024 |
| `month(date)` | 月份 | `month('2024-01-15')` → 1 |
| `day(date)` | 日期 | `day('2024-01-15')` → 15 |
| `dayofmonth(date)` | 月内日期（别名） | `dayofmonth('2024-01-15')` → 15 |
| `dayofweek(date)` | 星期几 | `dayofweek('2024-01-15')` → 2 (周一) |
| `dayofyear(date)` | 年内天数 | `dayofyear('2024-01-15')` → 15 |
| `hour(timestamp)` | 小时 | `hour('10:30:00')` → 10 |
| `minute(timestamp)` | 分钟 | `minute('10:30:00')` → 30 |
| `second(timestamp)` | 秒 | `second('10:30:45')` → 45 |
| `quarter(date)` | 季度 | `quarter('2024-01-15')` → 1 |
| `weekofyear(date)` | 年内周数 | `weekofyear('2024-01-15')` → 3 |
| `date_add(date, days)` | 日期加法 | `date_add('2024-01-15', 10)` → '2024-01-25' |
| `date_sub(date, days)` | 日期减法 | `date_sub('2024-01-15', 10)` → '2024-01-05' |
| `datediff(date1, date2)` | 日期差 | `datediff('2024-01-15', '2024-01-01')` → 14 |
| `add_months(date, months)` | 添加月份 | `add_months('2024-01-15', 2)` → '2024-03-15' |
| `last_day(date)` | 月末日期 | `last_day('2024-01-15')` → '2024-01-31' |
| `next_day(date, dayOfWeek)` | 下一个工作日 | `next_day('2024-01-15', 'MON')` → '2024-01-22' |
| `trunc(date, fmt)` | 截断日期 | `trunc('2024-01-15', 'MM')` → '2024-01-01' |
| `date_trunc(fmt, date)` | 截断日期（别名） | `date_trunc('month', '2024-01-15')` → '2024-01-01' |
| `from_unixtime(unix_time)` | Unix时间戳转日期 | `from_unixtime(1640000000)` → '2021-12-20...' |
| `unix_timestamp(timestamp)` | 日期转Unix时间戳 | `unix_timestamp('2024-01-15')` → 1705276800 |
| `to_date(str)` | 字符串转日期 | `to_date('2024-01-15')` → DATE '2024-01-15' |
| `to_timestamp(str)` | 字符串转时间戳 | `to_timestamp('2024-01-15 10:30:00')` → TIMESTAMP '...' |

### B.1.5 类型转换函数

| 函数 | 说明 | 示例 |
|------|------|------|
| `cast(x AS type)` | 类型转换 | `cast('123' AS INT)` → 123 |
| `try_cast(x AS type)` | 安全类型转换 | `try_cast('abc' AS INT)` → NULL |
| `typeof(x)` | 返回类型 | `typeof(123)` → 'INTEGER' |

### B.1.6 条件函数

| 函数 | 说明 | 示例 |
|------|------|------|
| `if(cond, true_val, false_val)` | 条件表达式 | `if(1 > 0, 'yes', 'no')` → 'yes' |
| `case when ... then ... end` | Case 表达式 | `case when x > 0 then 'pos' else 'neg' end` |
| `coalesce(val1, val2, ...)` | 返回第一个非NULL值 | `coalesce(NULL, NULL, 'default')` → 'default' |
| `nvl(val1, val2)` | 替换NULL | `nvl(NULL, 'default')` → 'default' |
| `nullif(val1, val2)` | 相等返回NULL | `nullif(1, 1)` → NULL |
| `ifnull(val1, val2)` | 替换NULL（别名） | `ifnull(NULL, 0)` → 0 |
| `greatest(val1, val2, ...)` | 最大值 | `greatest(1, 5, 3)` → 5 |
| `least(val1, val2, ...)` | 最小值 | `least(1, 5, 3)` → 1 |

### B.1.7 聚合函数

| 函数 | 说明 | 示例 |
|------|------|------|
| `count(*)` | 计数 | `SELECT count(*) FROM table` |
| `count(col)` | 非NULL计数 | `SELECT count(user_id) FROM table` |
| `count(distinct col)` | 去重计数 | `SELECT count(distinct user_id) FROM table` |
| `sum(col)` | 求和 | `SELECT sum(amount) FROM table` |
| `avg(col)` | 平均值 | `SELECT avg(age) FROM table` |
| `min(col)` | 最小值 | `SELECT min(price) FROM table` |
| `max(col)` | 最大值 | `SELECT max(price) FROM table` |
| `stddev(col)` | 标准差 | `SELECT stddev(value) FROM table` |
| `stddev_pop(col)` | 总体标准差 | `SELECT stddev_pop(value) FROM table` |
| `stddev_samp(col)` | 样本标准差 | `SELECT stddev_samp(value) FROM table` |
| `variance(col)` | 方差 | `SELECT variance(value) FROM table` |
| `var_pop(col)` | 总体方差 | `SELECT var_pop(value) FROM table` |
| `var_samp(col)` | 样本方差 | `SELECT var_samp(value) FROM table` |
| `collect_list(col)` | 收集为数组 | `SELECT collect_list(item) FROM table` |
| `collect_set(col)` | 收集为去重数组 | `SELECT collect_set(item) FROM table` |
| `first(col)` | 第一个值 | `SELECT first(value) FROM table` |
| `last(col)` | 最后一个值 | `SELECT last(value) FROM table` |
| `approx_distinct(col)` | 近似去重计数 | `SELECT approx_distinct(user_id) FROM table` |
| `approx_percentile(col, p)` | 近似百分位数 | `SELECT approx_percentile(latency, 0.95) FROM table` |

### B.1.8 窗口函数

| 函数 | 说明 | 示例 |
|------|------|------|
| `row_number()` | 行号 | `row_number() OVER (ORDER BY col)` |
| `rank()` | 排名（有间隙） | `rank() OVER (ORDER BY score DESC)` |
| `dense_rank()` | 密集排名 | `dense_rank() OVER (ORDER BY score DESC)` |
| `percent_rank()` | 百分比排名 | `percent_rank() OVER (ORDER BY col)` |
| `ntile(n)` | N分位数 | `ntile(4) OVER (ORDER BY col)` |
| `lead(col, n, default)` | 向前偏移 | `lead(value, 1) OVER (ORDER BY date)` |
| `lag(col, n, default)` | 向后偏移 | `lag(value, 1) OVER (ORDER BY date)` |
| `first_value(col)` | 窗口第一个值 | `first_value(col) OVER (PARTITION BY id ORDER BY date)` |
| `last_value(col)` | 窗口最后一个值 | `last_value(col) OVER (PARTITION BY id ORDER BY date)` |
| `nth_value(col, n)` | 窗口第N个值 | `nth_value(col, 2) OVER (PARTITION BY id ORDER BY date)` |

### B.1.9 数组函数

| 函数 | 说明 | 示例 |
|------|------|------|
| `array(val1, val2, ...)` | 创建数组 | `array(1, 2, 3)` → [1, 2, 3] |
| `array_contains(arr, val)` | 包含检查 | `array_contains(array(1,2,3), 2)` → true |
| `array_distinct(arr)` | 数组去重 | `array_distinct(array(1,2,2,3))` → [1, 2, 3] |
| `array_max(arr)` | 数组最大值 | `array_max(array(1,5,3))` → 5 |
| `array_min(arr)` | 数组最小值 | `array_min(array(1,5,3))` → 1 |
| `array_position(arr, val)` | 元素位置 | `array_position(array('a','b','c'), 'b')` → 2 |
| `array_remove(arr, val)` | 移除元素 | `array_remove(array(1,2,3,2), 2)` → [1, 3] |
| `array_sort(arr)` | 数组排序 | `array_sort(array(3,1,2))` → [1, 2, 3] |
| `array_union(arr1, arr2)` | 数组并集 | `array_union(array(1,2), array(2,3))` → [1, 2, 3] |
| `arrays_overlap(arr1, arr2)` | 数组重叠检查 | `arrays_overlap(array(1,2), array(2,3))` → true |
| `flatten(arr)` | 展平嵌套数组 | `flatten(array(array(1,2), array(3,4)))` → [1,2,3,4] |
| `size(arr)` | 数组大小 | `size(array(1,2,3))` → 3 |
| `slice(arr, start, length)` | 数组切片 | `slice(array(1,2,3,4), 2, 2)` → [2, 3] |

### B.1.10 Map 函数

| 函数 | 说明 | 示例 |
|------|------|------|
| `map(key1, val1, key2, val2, ...)` | 创建Map | `map('a', 1, 'b', 2)` → {'a': 1, 'b': 2} |
| `map_keys(map)` | Map键 | `map_keys(map('a', 1))` → ['a'] |
| `map_values(map)` | Map值 | `map_values(map('a', 1))` → [1] |
| `map_concat(map1, map2, ...)` | 合并Map | `map_concat(map('a',1), map('b',2))` → {'a':1, 'b':2} |
| `element_at(map, key)` | 获取Map值 | `element_at(map('a', 1), 'a')` → 1 |

### B.1.11 JSON 函数

| 函数 | 说明 | 示例 |
|------|------|------|
| `get_json_object(json, path)` | 提取JSON字段 | `get_json_object('{"a":1}', '$.a')` → '1' |
| `json_tuple(json, f1, f2, ...)` | 提取多个JSON字段 | `json_tuple('{"a":1,"b":2}', 'a', 'b')` → (1, 2) |
| `from_json(json, schema)` | JSON转结构体 | `from_json('{"a":1}', 'a INT')` → {a: 1} |
| `to_json(struct)` | 结构体转JSON | `to_json(struct(1 as a))` → '{"a":1}' |

## B.2 ClickHouse 后端支持的函数

ClickHouse 后端支持 **1000+ 函数**，这里列出最常用的函数。完整列表请参考 ClickHouse 官方文档。

### B.2.1 数学函数（扩展）

除了 Velox 支持的函数外，ClickHouse 还支持：

| 函数 | 说明 | 示例 |
|------|------|------|
| `cbrt(x)` | 立方根 | `cbrt(27)` → 3.0 |
| `erf(x)` | 误差函数 | `erf(1)` → 0.8427... |
| `erfc(x)` | 补误差函数 | `erfc(1)` → 0.1572... |
| `lgamma(x)` | 对数Gamma函数 | `lgamma(5)` → 3.178... |
| `tgamma(x)` | Gamma函数 | `tgamma(5)` → 24.0 |
| `gcd(a, b)` | 最大公约数 | `gcd(12, 8)` → 4 |
| `lcm(a, b)` | 最小公倍数 | `lcm(12, 8)` → 24 |

### B.2.2 字符串函数（扩展）

| 函数 | 说明 | 示例 |
|------|------|------|
| `base64Encode(str)` | Base64编码 | `base64Encode('Hello')` → 'SGVsbG8=' |
| `base64Decode(str)` | Base64解码 | `base64Decode('SGVsbG8=')` → 'Hello' |
| `urlEncode(str)` | URL编码 | `urlEncode('a b')` → 'a+b' |
| `urlDecode(str)` | URL解码 | `urlDecode('a+b')` → 'a b' |
| `format(fmt, ...)` | 格式化字符串 | `format('{} {}', 'Hello', 'World')` → 'Hello World' |
| `leftPad(str, len, pad)` | 左填充 | `leftPad('5', 3, '0')` → '005' |
| `rightPad(str, len, pad)` | 右填充 | `rightPad('5', 3, '0')` → '500' |
| `startsWith(str, prefix)` | 前缀检查 | `startsWith('Hello', 'He')` → true |
| `endsWith(str, suffix)` | 后缀检查 | `endsWith('Hello', 'lo')` → true |

### B.2.3 聚合函数（扩展）

| 函数 | 说明 | 示例 |
|------|------|------|
| `any(col)` | 任意一个值 | `SELECT any(value) FROM table` |
| `anyLast(col)` | 最后一个值 | `SELECT anyLast(value) FROM table` |
| `argMin(arg, val)` | 最小值对应的arg | `SELECT argMin(name, age) FROM users` |
| `argMax(arg, val)` | 最大值对应的arg | `SELECT argMax(name, age) FROM users` |
| `groupArray(col)` | 收集为数组 | `SELECT groupArray(item) FROM table` |
| `groupUniqArray(col)` | 收集为去重数组 | `SELECT groupUniqArray(item) FROM table` |
| `groupBitAnd(col)` | 位与 | `SELECT groupBitAnd(flags) FROM table` |
| `groupBitOr(col)` | 位或 | `SELECT groupBitOr(flags) FROM table` |
| `groupBitXor(col)` | 位异或 | `SELECT groupBitXor(flags) FROM table` |
| `groupBitmap(col)` | Bitmap聚合 | `SELECT groupBitmap(user_id) FROM table` |
| `uniq(col)` | 精确去重计数 | `SELECT uniq(user_id) FROM table` |
| `uniqExact(col)` | 精确去重计数（别名） | `SELECT uniqExact(user_id) FROM table` |
| `uniqCombined(col)` | 近似去重计数（HLL） | `SELECT uniqCombined(user_id) FROM table` |
| `quantile(level)(col)` | 分位数 | `SELECT quantile(0.95)(latency) FROM table` |
| `quantiles(level1, level2, ...)(col)` | 多个分位数 | `SELECT quantiles(0.5, 0.95, 0.99)(latency) FROM table` |
| `median(col)` | 中位数 | `SELECT median(value) FROM table` |
| `topK(k)(col)` | 最高频的K个值 | `SELECT topK(10)(product_id) FROM sales` |

### B.2.4 时间序列函数

| 函数 | 说明 | 示例 |
|------|------|------|
| `toStartOfInterval(date, interval)` | 时间间隔起始 | `toStartOfInterval(now(), INTERVAL 1 hour)` |
| `toStartOfFiveMinutes(datetime)` | 5分钟起始 | `toStartOfFiveMinutes(now())` |
| `toStartOfTenMinutes(datetime)` | 10分钟起始 | `toStartOfTenMinutes(now())` |
| `toStartOfFifteenMinutes(datetime)` | 15分钟起始 | `toStartOfFifteenMinutes(now())` |
| `toStartOfHour(datetime)` | 小时起始 | `toStartOfHour(now())` |
| `toStartOfDay(date)` | 日期起始 | `toStartOfDay(now())` |
| `toStartOfWeek(date)` | 周起始（周一） | `toStartOfWeek(now())` |
| `toStartOfMonth(date)` | 月起始 | `toStartOfMonth(now())` |
| `toStartOfQuarter(date)` | 季度起始 | `toStartOfQuarter(now())` |
| `toStartOfYear(date)` | 年起始 | `toStartOfYear(now())` |

### B.2.5 BitMap 函数

| 函数 | 说明 | 示例 |
|------|------|------|
| `bitmapBuild(arr)` | 构建Bitmap | `bitmapBuild(array(1,2,3))` |
| `bitmapToArray(bitmap)` | Bitmap转数组 | `bitmapToArray(bitmap)` → [1,2,3] |
| `bitmapContains(bitmap, val)` | 包含检查 | `bitmapContains(bitmap, 5)` → true/false |
| `bitmapCardinality(bitmap)` | Bitmap基数 | `bitmapCardinality(bitmap)` → 1000 |
| `bitmapAnd(bitmap1, bitmap2)` | Bitmap交集 | `bitmapAnd(bm1, bm2)` |
| `bitmapOr(bitmap1, bitmap2)` | Bitmap并集 | `bitmapOr(bm1, bm2)` |
| `bitmapXor(bitmap1, bitmap2)` | Bitmap异或 | `bitmapXor(bm1, bm2)` |

### B.2.6 IP 地址函数

| 函数 | 说明 | 示例 |
|------|------|------|
| `IPv4NumToString(num)` | IP数字转字符串 | `IPv4NumToString(3232235777)` → '192.168.1.1' |
| `IPv4StringToNum(str)` | IP字符串转数字 | `IPv4StringToNum('192.168.1.1')` → 3232235777 |
| `IPv4CIDRToRange(ip, cidr)` | CIDR转IP范围 | `IPv4CIDRToRange('192.168.1.0', 24)` |
| `IPv6NumToString(num)` | IPv6转字符串 | `IPv6NumToString(num)` |
| `IPv6StringToNum(str)` | 字符串转IPv6 | `IPv6StringToNum('::1')` |

### B.2.7 URL 函数

| 函数 | 说明 | 示例 |
|------|------|------|
| `protocol(url)` | 提取协议 | `protocol('https://example.com')` → 'https' |
| `domain(url)` | 提取域名 | `domain('https://example.com/path')` → 'example.com' |
| `topLevelDomain(url)` | 顶级域名 | `topLevelDomain('example.com')` → 'com' |
| `path(url)` | 提取路径 | `path('https://example.com/a/b')` → '/a/b' |
| `pathFull(url)` | 完整路径（含参数） | `pathFull('https://example.com/a?x=1')` → '/a?x=1' |
| `queryString(url)` | 查询字符串 | `queryString('https://example.com?a=1')` → 'a=1' |
| `fragment(url)` | URL片段 | `fragment('https://example.com#section')` → 'section' |
| `extractURLParameter(url, name)` | 提取URL参数 | `extractURLParameter('?a=1&b=2', 'a')` → '1' |

## B.3 函数兼容性对比

### B.3.1 核心函数支持对比

| 功能类别 | Velox 支持数 | ClickHouse 支持数 | 说明 |
|---------|------------|------------------|------|
| 数学函数 | ~30 | ~50 | ClickHouse 支持更多高级数学函数 |
| 字符串函数 | ~40 | ~100 | ClickHouse 支持编码/解码等扩展功能 |
| 日期时间 | ~35 | ~80 | ClickHouse 对时间序列支持更好 |
| 聚合函数 | ~25 | ~60 | ClickHouse 支持更多近似聚合 |
| 窗口函数 | ~15 | ~15 | 基本相同 |
| 数组函数 | ~20 | ~50 | ClickHouse 支持更丰富的数组操作 |
| Map/JSON | ~10 | ~30 | ClickHouse 支持更复杂的嵌套结构 |
| 特殊函数 | 少 | 多 | ClickHouse 支持Bitmap、IP、URL等专用函数 |

### B.3.2 Spark SQL 兼容性

| Spark SQL 函数 | Velox | ClickHouse | 备注 |
|---------------|-------|------------|------|
| 基础数学函数 | ✅ | ✅ | 完全兼容 |
| 基础字符串函数 | ✅ | ✅ | 完全兼容 |
| 基础日期函数 | ✅ | ✅ | 完全兼容 |
| 基础聚合函数 | ✅ | ✅ | 完全兼容 |
| 窗口函数 | ✅ | ✅ | 完全兼容 |
| 高级聚合（percentile_approx） | ✅ | ✅ | 完全兼容 |
| Spark UDF | ❌ | ❌ | 需要 Fallback |
| Hive UDF | ❌ | ❌ | 需要 Fallback |

## B.4 函数使用示例

### B.4.1 复杂查询示例

```sql
-- 用户行为分析（使用多种函数）
SELECT 
    user_id,
    count(*) as event_count,
    count(distinct session_id) as session_count,
    collect_list(event_type) as event_sequence,
    min(event_time) as first_event,
    max(event_time) as last_event,
    datediff(max(event_time), min(event_time)) as active_days,
    approx_percentile(duration, 0.95) as p95_duration,
    avg(duration) as avg_duration,
    sum(if(event_type = 'purchase', amount, 0)) as total_purchase
FROM events
WHERE date >= '2024-01-01'
GROUP BY user_id
HAVING event_count > 10
ORDER BY total_purchase DESC
LIMIT 100
```

### B.4.2 窗口函数示例

```sql
-- 每个用户的消费排名和移动平均
SELECT 
    user_id,
    date,
    amount,
    row_number() OVER (PARTITION BY user_id ORDER BY date) as purchase_seq,
    rank() OVER (PARTITION BY user_id ORDER BY amount DESC) as amount_rank,
    avg(amount) OVER (
        PARTITION BY user_id 
        ORDER BY date 
        ROWS BETWEEN 6 PRECEDING AND CURRENT ROW
    ) as ma7_amount,
    sum(amount) OVER (
        PARTITION BY user_id 
        ORDER BY date
    ) as cumulative_amount
FROM purchases
```

### B.4.3 数组和JSON函数示例

```sql
-- 处理复杂嵌套数据
SELECT 
    user_id,
    get_json_object(profile, '$.name') as user_name,
    get_json_object(profile, '$.age') as age,
    size(interests) as interest_count,
    array_contains(interests, 'sports') as likes_sports,
    array_max(scores) as best_score,
    flatten(array(tags, interests)) as all_tags
FROM users
```

## B.5 性能建议

### B.5.1 函数性能对比

| 函数类型 | 性能 | 建议 |
|---------|------|------|
| 简单数学函数 | 极快 | 放心使用 |
| 字符串连接/分割 | 快 | 注意大规模字符串 |
| 正则表达式 | 中等 | 尽量简化正则 |
| JSON解析 | 中等-慢 | 考虑预处理 |
| 近似聚合 | 快 | 优于精确聚合 |
| 窗口函数 | 慢 | 注意分区大小 |

### B.5.2 优化建议

1. **使用近似函数**：
```sql
-- 慢
SELECT count(distinct user_id) FROM large_table

-- 快（误差 <2%）
SELECT approx_distinct(user_id) FROM large_table
```

2. **避免复杂正则**：
```sql
-- 慢
WHERE regexp_like(text, '非常复杂的正则.*')

-- 快
WHERE text LIKE '%keyword%'
```

3. **预计算JSON字段**：
```sql
-- 慢（每次解析）
SELECT get_json_object(json_col, '$.field') FROM table

-- 快（创建虚拟列）
ALTER TABLE table ADD COLUMN field_extracted STRING AS (get_json_object(json_col, '$.field'))
```

---

**本附录完**。完整的函数文档请参考官方文档：
- Velox：https://facebookincubator.github.io/velox/functions.html
- ClickHouse：https://clickhouse.com/docs/en/sql-reference/functions/
