<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>

<%@ taglib prefix="cron"  uri="http://www.opencron.org"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>
<!DOCTYPE html>
<html lang="en">
<meta name="author" content="author:benjobs,wechat:wolfboys,Created by 2016" />
<head>
    <script type="text/javascript">
        $(document).ready(function () {
            var iframe = document.getElementById("druid");
            iframe.onload = function () {
                var valId = window.setInterval(function(){
                    var brand = $("#druid").contents().find(".brand");
                    if ( brand.text() ){
                        brand.attr("href","javascript:void(0)");
                        brand.removeAttr("target");
                        brand.text("Opencron Druid");
                        window.clearInterval(valId);
                    }
                }, 1);
                var footer = $("#druid").contents().find(".footer");
                footer.find(".container").remove();
            }
        });
        function reinitIframe() {
            try{
                var iframe = document.getElementById("druid");
                var bHeight = iframe.contentWindow.document.body.scrollHeight;
                var dHeight = iframe.contentWindow.document.documentElement.scrollHeight;
                var height = Math.max(bHeight, dHeight);
                iframe.height = height;
                window.frames["druid"].document.getElementById("iframe中控件的ID");
            }catch (ex){}
        }
        window.setInterval("reinitIframe()", 200);
    </script>
</head>

<body>
<!-- Content -->
<section id="content" class="container">

    <!-- Messages Drawer -->
    <jsp:include page="/WEB-INF/layouts/message.jsp"/>

    <!-- Breadcrumb -->
    <ol class="breadcrumb hidden-xs">
        <li class="icon">&#61753;</li>
        当前位置：
        <li><a href="">opencron</a></li>
        <li><a href="">监控中心</a></li>
        <li><a href="">Druid监控</a></li>
    </ol>
    <h4 class="page-title"><i class="fa fa-bar-chart" aria-hidden="true" style="font-style: 30px;"></i>&nbsp;Druid监控</h4>

    <div class="block-area">
        <iframe src="${contextPath}/druid/index.html" frameborder="0" scrolling="no" id="druid" name="druid" width="100%" onload="this.height=100"></iframe>
    </div>

</section>

</body>

</html>
