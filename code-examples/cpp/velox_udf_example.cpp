/**
 * Apache Gluten - Velox UDF 示例
 * 
 * 功能：演示如何编写 Velox Native UDF
 * 相关章节：第11章 - Velox 后端
 * 
 * 编译：
 * g++ -std=c++17 -fPIC -shared -I${VELOX_HOME}/include \
 *     velox_udf_example.cpp -o libvelox_udf.so
 */

#include <algorithm>
#include <string>

// 简化的示例，实际需要 Velox 头文件

namespace gluten {
namespace udf {

/**
 * 示例 UDF: 字符串转大写
 */
class MyUpperUDF {
public:
  static std::string execute(const std::string& input) {
    std::string result = input;
    std::transform(result.begin(), result.end(), result.begin(), ::toupper);
    return result;
  }
};

/**
 * 示例 UDF: 加权平均
 */
class WeightedAverageUDF {
public:
  static double execute(int64_t val1, int64_t val2, double weight) {
    return val1 * weight + val2 * (1.0 - weight);
  }
};

} // namespace udf
} // namespace gluten

/*
 * 使用说明：
 * 
 * 1. 编译为共享库
 * 2. 在 Spark 中加载: spark.jars=/path/to/libvelox_udf.so
 * 3. SQL 中使用: SELECT my_upper(name) FROM table
 */
