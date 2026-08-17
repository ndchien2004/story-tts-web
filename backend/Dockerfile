# Bản dựng hai tầng: tầng đầu cần cả Maven lẫn JDK, ảnh chạy thì chỉ cần JRE.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests clean package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /build/target/*.jar app.jar

# Chạy bằng tài khoản không đặc quyền. Ứng dụng chỉ cần đọc app.jar và mở một
# cổng cao, không cần gì tới quyền quản trị — mà mặc định của ảnh nền là root,
# nên phải nói ra thì mới thôi. Nếu một lỗ hổng nào đó cho phép chạy lệnh trong
# tiến trình này, đây là ranh giới quyết định lệnh ấy làm được tới đâu.
RUN useradd --system --uid 10001 --no-create-home storytts \
    && chown storytts /app/app.jar
USER storytts

# Cấu hình cho hộp 512MB - gói miễn phí của Render. Heap chỉ là một phần chỗ
# JVM chiếm, nên đặt heap sát trần là cách chắc chắn nhất để bị hạ tiến trình:
# Metaspace, vùng mã đã biên dịch và ngăn xếp của từng luồng đều nằm ngoài heap
# và cộng dồn vào cùng cái trần ấy. Chia phần rõ ràng cho từng vùng thì tổng
# mới nằm dưới 512MB.
#
#   Xmx224m              heap
#   MaxMetaspaceSize     Spring nạp rất nhiều lớp; không chặn thì nó cứ phình
#   ReservedCodeCache    đi cùng TieredStopAtLevel=1 bên dưới
#   Xss512k              mặc định 1MB, nhân với số luồng Tomcat là đáng kể
#   UseSerialGC          G1 giữ sẵn cấu trúc vùng nhớ, thừa thãi với heap nhỏ
#   TieredStopAtLevel=1  chỉ biên dịch tầng một: khởi động nhanh, tốn ít bộ nhớ,
#                        đổi lại chạy chậm hơn khi tải nặng - đánh đổi đúng cho
#                        một bản demo
#
# Để trong biến môi trường để chuyển sang máy chủ rộng rãi hơn thì chỉ cần đặt
# lại JAVA_OPTS, không phải dựng lại ảnh.
ENV JAVA_OPTS="-Xmx224m -Xss512k -XX:MaxMetaspaceSize=120m -XX:ReservedCodeCacheSize=32m -XX:MaxDirectMemorySize=32m -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -XX:+ExitOnOutOfMemoryError"
# exec để java thành tiến trình số 1 và nhận được tín hiệu dừng của Docker;
# thiếu nó thì mỗi lần triển khai lại phải chờ hết thời gian rồi bị giết cứng.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
