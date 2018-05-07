Number.prototype.getChar = function(){
    var arrays = "A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,Q,R,S,T,U,V,W,X,Y,Z".split(",");
    return arrays[this];
}

function Validata() {

    this.contextPath = arguments[0]||'';
    this.jobId = arguments[1]||null;

    var self = this;

    this.validata =  {

        status: true,

        jobNameRemote:false,

        cronExpRemote:false,

        init: function () {
            this.status = true;
            this.jobNameRemote = false;
            this.cronExpRemote = false;
        },

        jobName: function () {
            var prefix = arguments[0] || "";
            var _jobName = $("#jobName" + prefix).val();
            if (!_jobName) {
                jobx.tipError("#jobName" + prefix, "必填项,作业名称不能为空");
                this.status = false;
            } else {
                if (_jobName.length < 4 || _jobName.length > 51) {
                    jobx.tipError("#jobName" + prefix, "作业名称不能小于4个字符并且不能超过50个字符!");
                    this.status = false;
                } else {
                    var _this = this;
                    $.ajax({
                        type: "POST",
                        url: self.contextPath+"/job/checkname.do",
                        data: {
                            "jobId":self.jobId,
                            "name": _jobName,
                            "agentId": $("#agentId").val()
                        }
                    }).done(function (data) {
                        _this.jobNameRemote = true;
                        if (!data) {
                            jobx.tipError("#jobName" + prefix, "作业名称已存在!");
                            _this.status = false;
                        } else {
                            jobx.tipOk("#jobName" + prefix);
                        }
                    }).fail(function () {
                        _this.jobNameRemote = true;
                        _this.status = false;
                        jobx.tipError("#jobName" + prefix, "网络请求错误,请重试!");
                    });
                }
            }
        },

        cronExp: function () {
            var cronType = $('input[type="radio"][name="cronType"]:checked').val();

            if(cronType == 0){
                $('#cronSelector').slideUp()
            }

            if ( !arguments[0] && cronType == 0) {
                $("#cronTip").css("visibility","visible").html("crontab: unix/linux的时间格式表达式 ");
            }
            if ( !arguments[0] && cronType == 1) {
                $("#cronTip").css("visibility","visible").html('quartz: quartz框架的时间格式表达式');
            }

            var cronExp = $("#cronExp").val();
            if (!cronExp) {
                jobx.tipError($("#cronExp"),"时间规则不能为空,请填写时间规则");
                this.status = false;
            } else {
                var _this = this;
                $.ajax({
                    type: "POST",
                    url: self.contextPath+"/verify/exp.do",
                    data: {
                        "cronType": cronType,
                        "cronExp": cronExp
                    },
                    dataType:"json"
                }).done(function (data) {
                    _this.cronExpRemote = true;
                    if (data.status) {
                        jobx.tipOk($("#expTip"));
                    } else {
                        self.toggle.cronTip(cronType);
                        $("#expTip").css("visibility","visible");
                        jobx.tipError($("#expTip"), "时间规则语法错误!");
                        _this.status = false;
                    }
                }).fail(function () {
                    _this.cronExpRemote = true;
                    jobx.tipError($("#expTip"), "网络请求错误,请重试!");
                    _this.status = false;
                });
            }
        },

        command: function () {
            var prefix = arguments[0] || "";
            if ($("#cmd" + prefix).val().length == 0) {
                jobx.tipError("#cmd" + prefix, "执行命令不能为空,请填写执行命令");
                this.status = false;
            } else {
                jobx.tipOk("#cmd" + prefix);
            }
        },

        successExit: function () {
            var prefix = arguments[0] || "";
            var successExit = $("#successExit" + prefix).val();
            if (successExit.length == 0) {
                jobx.tipError("#successExit" + prefix, "自定义成功标识不能为空");
                this.status = false;
            } else if (isNaN(successExit)) {
                jobx.tipError("#successExit" + prefix, "自定义成功标识必须为数字");
                this.status = false;
            } else {
                jobx.tipOk("#successExit" + prefix);
            }
        },

        runCount: function () {
            var prefix = arguments[0] || "";
            var redo = prefix ? $("#itemRedo").val() : $('input[type="radio"][name="redo"]:checked').val();
            var reg = /^[0-9]*[1-9][0-9]*$/;
            if (redo == 1) {
                var _runCount = $("#runCount" + prefix).val();
                if (!_runCount) {
                    jobx.tipError("#runCount" + prefix, "请填写重跑次数!");
                    this.status = false;
                } else if (!reg.test(_runCount)) {
                    jobx.tipError("#runCount" + prefix, "截止重跑次数必须为正整数!");
                    this.status = false;
                } else {
                    jobx.tipOk("#runCount" + prefix);
                }
            }
        },

        subJob: function () {
            if ($('input[name="jobType"]:checked').val() == 1) {
                if ($("#subJobDiv:has(li)").length == 0) {
                    jobx.tipError($("#jobTypeTip"), "当前是流程作业,请至少添加一个子作业!");
                    this.status = false;
                }
            }
        },

        mobiles: function () {
            var mobiles = $("#mobiles").val();
            if (!mobiles) {
                jobx.tipError("#mobiles", "请填写手机号码!");
                this.status = false;
                return;
            }
            var mobs = mobiles.split(",");
            var verify = true;
            for (var i in mobs) {
                if (!jobx.testMobile(mobs[i])) {
                    this.status = false;
                    verify = false;
                    jobx.tipError("#mobiles", "请填写正确的手机号码!");
                    break;
                }
            }
            if (verify) {
                jobx.tipOk("#mobiles");
            }
        },

        email: function () {
            var emails = $("#email").val();
            if (!emails) {
                jobx.tipError("#email", "请填写邮箱地址!");
                this.status = false;
                return;
            }
            var emas = emails.split(",");
            var verify = true;
            for (var i in emas) {
                if (!jobx.testEmail(emas[i])) {
                    jobx.tipError("#email", "请填写正确的邮箱地址!");
                    this.status = false;
                    verify = false;
                    break;
                }
            }
            if (verify) {
                jobx.tipOk("#email");
            }
        },

        timeout: function () {
            var prefix = arguments[0] || "";
            var timeout = $("#timeout" + prefix).val();
            if (timeout.length > 0) {
                if (isNaN(timeout) || parseInt(timeout) < 0) {
                    jobx.tipError("#timeout" + prefix, "超时时间必须为正整数,请填写正确的超时时间!");
                    this.status = false;
                } else {
                    jobx.tipOk("#timeout" + prefix);
                }
            } else {
                this.status = false;
                jobx.tipError("#timeout" + prefix, "超时时间不能为空,请填写(0:忽略超时时间,分钟为单位!");
            }
        },

        warning: function () {
            var _warning = $('input[type="radio"][name="warning"]:checked').val();
            if (_warning == 1) {
                this.mobiles();
                this.email();
            }
        },

        verify: function () {
            this.init();
            this.jobName();
            this.cronExp();
            this.command();
            this.successExit();
            this.runCount();
            this.subJob();
            this.timeout();
            this.warning();
            return this.status;
        }
    };

    this.subJob = {

        jobFlagNum:0,

        tipDefault: function () {
            jobx.tipDefault("#jobName1");
            jobx.tipDefault("#cmd1");
            jobx.tipDefault("#successExit1");
            jobx.tipDefault("#timeout1");
            jobx.tipDefault("#runCount1");
            $("#jobModal").find(".ok").remove();
        },

        add: function () {
            $("#subForm")[0].reset();
            self.toggle.redo(1);
            this.tipDefault();
            $("#subTitle").html("添加作业依赖").attr("action", "add");
        },

        edit: function (id) {
            this.tipDefault();
            $("#subTitle").html("编辑作业依赖").attr("action", "edit").attr("tid", id);
            $("#" + id).find("input").each(function (index, element) {

                if ($(element).attr("name") == "child.jobName") {
                    $("#jobName1").val(unEscapeHtml($(element).val()));
                }
                if ($(element).attr("name") == "child.agentId") {
                    $("#agentId1").val($(element).val());
                }
                if ($(element).attr("name") == "child.command") {
                    $("#cmd1").val(passBase64($(element).val()));
                }

                if ($(element).attr("name") == "child.successExit") {
                    $("#successExit1").val($(element).val());
                }

                if ($(element).attr("name") == "child.redo") {
                    self.toggle.redo($("#itemRedo").val() || $(element).val());
                }

                if ($(element).attr("name") == "child.runCount") {
                    $("#runCount1").val($(element).val());
                }

                if ($(element).attr("name") == "child.timeout") {
                    $("#timeout1").val($(element).val());
                }

                if ($(element).attr("name") == "child.comment") {
                    $("#comment1").val(unEscapeHtml($(element).val()));
                }
            });
        },

        remove: function (node,num) {
            swal({
                title: "",
                text: "您确定要删除该作业吗？",
                type: "warning",
                showCancelButton: true,
                closeOnConfirm: true,
                confirmButtonText: "删除"
            },function () {
                alertMsg("删除作业成功");
                $(node).parent().slideUp(300, function () {
                    this.remove();
                    self.subJob.graphH(0);
                    var deps = $(".depen-input").val();
                    if (deps.length == 0) return;

                    var char = num.getChar();
                    var reg1 = char+">";
                    var reg2 = char+",";
                    var reg3 = ","+char;
                    var reg4 = ">"+char;

                    deps = deps.replace(reg1,"").replace(reg1.toLowerCase(),"")
                        .replace(reg2,"").replace(reg2.toLowerCase(),"")
                        .replace(reg3,"").replace(reg3.toLowerCase(),"")
                        .replace(reg4,"").replace(reg4.toLowerCase(),"")

                    $(".depen-input").val(deps);

                    graph(0);
                });
            });
        },

        close: function () {
            $("#subForm")[0].reset();
            $('#jobModal').modal('hide');
        },

        graphH:function(index) {
            var children = $("#subJobDiv").find(".jobnum").length;
            var graphH = (children+index) * 35 + 20;
            $(".graph").css({
                "margin-top":"-"+(200+graphH)+ "px",
                "height":200+graphH+"px"
            });
        },

        verify: function () {
            self.validata.init();
            self.validata.jobName("1");
            self.validata.command("1");
            self.validata.successExit("1");
            self.validata.timeout("1");
            self.validata.runCount("1");

            var valId = setInterval(function () {
                if (self.validata.jobNameRemote ) {
                    window.clearInterval(valId);
                    if (self.validata.status) {
                        //添加
                        var _jobName = $("#jobName1").val();
                        if ($("#subTitle").attr("action") === "add") {
                            var timestamp = Date.parse(new Date());
                            var children = $("#subJobDiv").find(".jobnum").length;
                            if (children == 0) {
                                var addHtml = "<li><span><div class='circle'></div><span class='jobnum' num='0' name='当前作业'>A</span>当前作业</span></li>";
                                $("#subJobDiv").show().append($(addHtml));
                            }

                            self.subJob.graphH(1);

                            var currNum = ++self.subJob.jobFlagNum;
                            var currJobNum = currNum.getChar();

                            var addHtml =
                                "<li id='" + timestamp + "'>" +
                                "<input type='hidden' name='child.jobId' value=''>" +
                                "<input type='hidden' name='child.jobName' num='"+currNum+"' value='" + escapeHtml(_jobName) + "'>" +
                                "<input type='hidden' name='child.agentId' value='" + $("#agentId1").val() + "'>" +
                                "<input type='hidden' name='child.command' value='" + toBase64($("#cmd1").val()) + "'>" +
                                "<input type='hidden' name='child.redo' value='" + $('#itemRedo').val() + "'>" +
                                "<input type='hidden' name='child.runCount' value='" + $("#runCount1").val() + "'>" +
                                "<input type='hidden' name='child.timeout' value='" + $("#timeout1").val() + "'>" +
                                "<input type='hidden' name='child.successExit' value='" + $("#successExit1").val() + "'>" +
                                "<input type='hidden' name='child.comment' value='" + escapeHtml($("#comment1").val()) + "'>" +
                                "<span id='name_" + timestamp + "'><div class='circle'></div><span class='jobnum' num='"+currNum+"' name='"+escapeHtml(_jobName)+"'>"+currJobNum+"</span>"  + escapeHtml(_jobName) + "</span>" +
                                "<span class='delSubJob' onclick='jobxValidata.subJob.remove(this,"+currNum+")' style='float:right; margin-right: 5px;'>" +
                                "   <i class='glyphicon glyphicon-trash' title='删除'></i>" +
                                "</span>" +
                                "<span onclick='jobxValidata.subJob.edit(\"" + timestamp + "\")' style='float:right; margin-right: 5px;'>" +
                                "   <a data-toggle='modal' href='#jobModal' title='编辑'>" +
                                "       <i class='glyphicon glyphicon-pencil'></i>&nbsp;&nbsp;" +
                                "   </a>" +
                                "</span>" +
                                "</li>";
                            $("#subJobDiv").show().append($(addHtml));
                        } else if ($("#subTitle").attr("action") == "edit") {//编辑
                            var id = $("#subTitle").attr("tid");
                            var currNum = 0;

                            $("#" + id).find("input").each(function (index, element) {
                                if ($(element).attr("name") == "child.jobName") {
                                    $(element).attr("value", _jobName);
                                    currNum =  parseInt($(element).attr("num"));
                                }

                                if ($(element).attr("name") == "child.redo") {
                                    $(element).attr("value", $('#itemRedo').val());
                                }

                                if ($(element).attr("name") == "child.runCount") {
                                    $(element).attr("value", $("#runCount1").val());
                                }

                                if ($(element).attr("name") == "child.successExit") {
                                    $(element).attr("value", $("#successExit1").val());
                                }

                                if ($(element).attr("name") == "child.agentId") {
                                    $(element).attr("value", $("#agentId1").val());
                                }
                                if ($(element).attr("name") == "child.command") {
                                    $(element).attr("value", toBase64($("#cmd1").val()));
                                }

                                if ($(element).attr("name") == "child.timeout") {
                                    $(element).attr("value", $("#timeout1").val());
                                }

                                if ($(element).attr("name") == "child.comment") {
                                    $(element).attr("value", $("#comment1").val());
                                }
                            });
                            var numHtml = "<div class='circle'></div><span class='jobnum' num='"+currNum+"' name='"+_jobName+"'>"+currNum.getChar()+"</span>";

                            $("#name_" + id).html(numHtml+escapeHtml(_jobName));

                            graph(1,currNum);

                        }
                        self.subJob.close();
                    }
                }
            },10);
        }
    };

    this.toggle = {
        redo: function (_toggle) {
            $("#itemRedo").val(_toggle);
            if (_toggle == 1) {
                $(".countDiv1").show();
                $("#redo1").prop("checked", true);
                $("#redo1").parent().removeClass("checked").addClass("checked");
                $("#redo1").parent().attr("aria-checked", true);
                $("#redo1").parent().prop("onclick", "showContact()");
                $("#redo0").parent().removeClass("checked");
                $("#redo0").parent().attr("aria-checked", false);
            } else {
                $(".countDiv1").hide();
                $("#redo0").prop("checked", true);
                $("#redo0").parent().removeClass("checked").addClass("checked");
                $("#redo0").parent().attr("aria-checked", true);
                $("#redo1").parent().removeClass("checked");
                $("#redo1").parent().attr("aria-checked", false);
            }
        },
        count: function (_toggle) {
            if (_toggle) {
                $("#redo").val(1);
                $(".countDiv").show()
            } else {
                $("#redo").val(0);
                $(".countDiv").hide();
            }
        },
        contact: function (_toggle) {
            if (_toggle) {
                $(".contact").show()
            } else {
                $(".contact").hide();
            }
        },
        cronTip: function (type) {
            if (type == 0) {
                $("#cronTip").css("visibility","visible").html("crontab: unix/linux的时间格式表达式 ");
            }
            if (type == 1) {
                $("#cronTip").css("visibility","visible").html('quartz: quartz框架的时间格式表达式');
            }
            if ( (arguments[1]||false) && $("#cronExp").val().length > 0) {
                self.validata.cronExp();
            }
        },

        subJob: function (_toggle) {
            if (_toggle == "1") {
                $("#jobTypeTip").html("流程作业: 有多个作业组成一个作业组");
                $(".subJob").show();
                $(".depen").show();
                $(".depen-input").val('');
            } else {
                $("#jobTypeTip").html("单一作业: 当前定义作业为要执行的目前作业");
                $(".subJob").hide();
                $(".depen").hide();
            }
        }
    };

    this.ready();
}

Validata.prototype.ready = function () {

    var _this = this;

    $("#cronType0").next().click(function () {
        _this.validata.cronExp();
    });
    $("#cronType0").parent().parent().click(function () {
        _this.validata.cronExp();
    });
    $("#cronType1").next().click(function () {
        _this.validata.cronExp();
    });
    $("#cronType1").parent().parent().click(function () {
        _this.validata.cronExp();
    });

    $("#redo01").next().click(function () {
        _this.toggle.count(true)
    });
    $("#redo01").parent().parent().click(function () {
        _this.toggle.count(true)
    });
    $("#redo00").next().click(function () {
        _this.toggle.count(false)
    });
    $("#redo00").parent().parent().click(function () {
        _this.toggle.count(false)
    });

    $("#redo1").next().click(function () {
        _this.toggle.redo(1);
    });
    $("#redo1").parent().parent().click(function () {
        _this.toggle.redo(1);
    })
    $("#redo0").next().click(function () {
        _this.toggle.redo(0);
    });
    $("#redo0").parent().parent().click(function () {
        _this.toggle.redo(0);
    });

    $("#jobType0").next().click(function () {
        _this.toggle.subJob(0);
    });
    $("#jobType0").parent().parent().click(function () {
        _this.toggle.subJob(0);
    });

    $("#jobType1").next().click(function () {
        _this.toggle.subJob(1);
    });
    $("#jobType1").parent().parent().click(function () {
        _this.toggle.subJob(1);
    });

    $("#warning0").next().click(function () {
        _this.toggle.contact(false);
    });
    $("#warning0").parent().parent().click(function () {
        _this.toggle.contact(false);
    });

    $("#warning1").next().click(function () {
        _this.toggle.contact(true);
    });
    $("#warning1").parent().parent().click(function () {
        _this.toggle.contact(true);
    });

    var redo = $('input[type="radio"][name="redo"]:checked').val();
    _this.toggle.count(redo == 1);

    var warning = $('input[type="radio"][name="warning"]:checked').val();
    _this.toggle.contact(warning == 1);

    _this.toggle.subJob($('input[type="radio"][name="jobType"]:checked').val());

    _this.toggle.cronTip($('input[type="radio"][name="cronType"]:checked').val());

    $("#year,#month,#day,#week,#hour,#minutes,#seconds").click(function () {
        var cronExp = "";
        var year = $("#year").val();
        if(year.length > 1 && year.indexOf("*") == 0){
            year = year.toString().substr(2);
        }
        var month = $("#month").val();
        if(month.length > 1 && month.indexOf("*") == 0){
            month = month.toString().substr(2);
        }
        var day = $("#day").val();
        if(day.length > 1 && day.indexOf("*") == 0){
            day = day.toString().substr(2);
        }
        var week = $("#week").val();
        if(!week){week="*";}
        if(week.length > 1 && week.indexOf("*") == 0){
            week = week.toString().substr(2);
        }
        if(week>0){
            week = parseInt(week)+1;
            if(week==8) week=1;
        }

        var hour = $("#hour").val();
        if(hour.length > 1 && hour.indexOf("*") == 0){
            hour = hour.toString().substr(2);
        }
        var minutes = $("#minutes").val();
        if(minutes.length > 1 && minutes.indexOf("*") == 0){
            minutes = minutes.toString().substr(2);
        }
        var seconds = $("#seconds").val();
        if(seconds.length > 1 && seconds.indexOf("*") == 0){
            seconds = seconds.toString().substr(2);
        }

        cronExp = seconds + " " + minutes + " " + hour + " " ;
        if(week == "*"){
            cronExp += day + " " + month + " ? ";
        }else if(day == "*" && week != "*"){
            cronExp += "? " + month + " " + week + " ";
        }else if(day != "*" && week != "*"){
            alert("日期和星期不能同时选择!");
            return false;
        }
        cronExp += year;
        $("#cronExp").val(cronExp);
    });

    $("#save-btn").click(function () {
        _this.validata.verify();
        if(_this.validata.status){
            var valId = setInterval(function () {
                if (_this.validata.jobNameRemote ) {
                    var cleanFlag = false;
                    if(_this.validata.cronExpRemote){
                        cleanFlag = true;
                    }
                    if(cleanFlag){
                        window.clearInterval(valId);
                        if(_this.validata.status) {
                            var cmd = $("#cmd").val();
                            $("#command").val(toBase64(cmd));
                            $("#jobform").submit();
                        }
                    }
                }
            },10);
        }
    });

    $("#subjob-btn").click(function () {
        _this.subJob.verify();
    });

    $("#remove-cron-btn").click(function () {
        $('#cronSelector').slideUp()
    });

    $("#jobName").blur(function () {
        _this.validata.jobName();
    }).focus(function () {
        jobx.tipDefault("#jobName");
    });

    $("#cronExp").blur(function () {
        _this.validata.cronExp();
    }).focus(function () {
        //var type = $('input[type="radio"][name="cronType"]:checked').val();
        if ($('#cronType0').prop("checked")) {
            $("#cronTip").css("visibility","visible").html("crontab: unix/linux的时间格式表达式 ");
            $("#expTip").css("visibility","visible").html('crontab: 请采用unix/linux的时间格式表达式,如 00 01 * * *');
        } else {
            $("#cronTip").css("visibility","visible").html('quartz: quartz框架的时间格式表达式');
            $("#expTip").css("visibility","visible").html('quartz: 请采用quartz框架的时间格式表达式,如 0 0 10 L * ?');
            $('#cronSelector').slideDown()
        }
    });


    $("#jobName1").blur(function () {
        _this.validata.jobName("1");
    }).focus(function () {
        jobx.tipDefault("#jobName1");
    });

    $("#cmd").blur(function () {
        _this.validata.command();
    }).focus(function () {
        jobx.tipDefault("#cmd");
    });
    $("#cmd1").blur(function () {
        _this.validata.command("1");
    }).focus(function () {
        jobx.tipDefault("#cmd1");
    });

    $("#runCount").blur(function () {
        _this.validata.runCount();
    }).focus(function () {
        jobx.tipDefault("#runCount");
    });
    $("#runCount1").blur(function () {
        _this.validata.runCount("1");
    }).focus(function () {
        jobx.tipDefault("#runCount1");
    });

    $("#successExit").blur(function () {
        _this.validata.successExit();
    }).focus(function () {
        jobx.tipDefault("#successExit");
    });
    $("#successExit1").blur(function () {
        _this.validata.successExit("1");
    }).focus(function () {
        jobx.tipDefault("#successExit1");
    });

    $("#timeout").blur(function () {
        _this.validata.timeout();
    }).focus(function () {
        jobx.tipDefault("#timeout");
    });
    $("#timeout1").blur(function () {
        _this.validata.timeout("1");
    }).focus(function () {
        jobx.tipDefault("#timeout1");
    });

    $("#mobiles").blur(function () {
        _this.validata.mobiles();
    }).focus(function () {
        jobx.tipDefault("#mobiles");
    });

    $("#email").blur(function () {
        _this.validata.email();
    }).focus(function () {
        jobx.tipDefault("#email");
    });
};
