let socket;

window.onload = function() {
    let childFrame = document.getElementById('frameElement');
    // Initialize Firebase
    firebase.initializeApp({
        apiKey: "AIzaSyCScK2DGrTe-lSdi7euF81ilaLygrEMcYo",
        authDomain: "omokgame-8c565.firebaseapp.com",
        projectId: "omokgame-8c565",
        storageBucket: "omokgame-8c565.appspot.com",
        messagingSenderId: "626400468296",
        appId: "1:626400468296:web:f146d30869018aa54de77d",
        measurementId: "G-M46FN291B5"
    });

    const messaging = firebase.messaging();
    const publicVapidKey = 'BDpyANOK-A_F167S6epNvW9p6Z-l_bUoZem0gyW8jgnHaRpTyqbk4UjOP7PzLdX6qdLHMoUynaj_4_3exq8PNuE';
    messaging.usePublicVapidKey(publicVapidKey);
    messaging.getToken().then(function(currentToken) {
        console.log('currentToken: ' + currentToken);
        if (currentToken) {
            childFrame.contentWindow.document.querySelector('#token').value = currentToken;
            console.log ('Token get - ' + currentToken);
        } else {
            console.log('No Instance ID token available. Request permission to generate one.');
        }
    }).catch(function(err) {
        console.log('Error retrieving Instance ID token. ', err);
    })

    socket = new WebSocket('ws://' + ip + ':8080/ws');
    socket.onopen = function (event) {
        console.log("open socket")
        socket.send(JSON.stringify({type: 'connect',token : childFrame.contentWindow.document.querySelector('#token').value}));
    };

    socket.onmessage = function (event) {
        // 서버로부터 수신된 메시지 처리 (게임 상태 업데이트 등)
        let receivedMessage;
        if (isJSON(event.data)) {
            receivedMessage = JSON.parse(event.data);
        }
        // 플레이어 간 통신 메시지 처리 로직...
        switch (receivedMessage.type) {
            case 'login' :
                if(receivedMessage.data.indexOf('입력한 정보가 올바르지 않습니다.') == -1) {
                    document.querySelector('#myId').innerText = '아이디 : ' + receivedMessage.data;
                } else {
                    alertPopup(receivedMessage.data);
                }
                childFrame.contentWindow.postMessage(receivedMessage, '*');
                break;
            case 'error' :
                childFrame.contentWindow.postMessage(receivedMessage, '*');
                break;
            case 'start' :
                childFrame.contentWindow.postMessage(receivedMessage, '*');
                break;
            case 'end' :
                childFrame.contentWindow.postMessage(receivedMessage, '*');
                break;
            case 'message' :
                childFrame.contentWindow.postMessage(receivedMessage, '*');
                break;
            case 'sendMessage' :
                childFrame.contentWindow.postMessage(receivedMessage, '*');
                break;
            case 'roomList' :
                childFrame.contentWindow.postMessage(receivedMessage, '*');
                break;
            case 'createRoom' :
                break;
            case 'joinRoom' :
                childFrame.contentWindow.document.querySelector('.waitingRoom').style.display = 'none';
                childFrame.contentWindow.document.querySelector('.gaming-room').style.display = '';
                childFrame.contentWindow.postMessage(receivedMessage, '*');
                break;
            case 'leaveRoom' :
                childFrame.contentWindow.document.querySelector('.waitingRoom').style.display = '';
                childFrame.contentWindow.document.querySelector('.gaming-room').style.display = 'none';
                break;
            case 'changeRole'  :
                childFrame.contentWindow.postMessage(receivedMessage, '*');
                break;
            case 'move' :
                childFrame.contentWindow.postMessage(receivedMessage, '*');
                break;
            case 'invite' :
                childFrame.contentWindow.postMessage(receivedMessage, '*');
                break;
            case 'idChk' :
                if(receivedMessage.data.indexOf('Success') == -1){
                    resetPwdAlertPopup(receivedMessage.data);
                    return;
                }
                document.querySelector('#resetPwd').style.display = 'none';
                document.querySelector('#savePwd').style.display = '';
                document.querySelector('#idGroup').style.display = 'none';
                document.querySelector('#emailGroup').style.display = 'none';
                document.querySelector('#pwdGroup').style.display = '';
                document.querySelector('#pwdChkGroup').style.display = '';
                break;
            case 'savePwd' :
                resetPwdAlertPopup(receivedMessage.data);
                break;
            default :
                childFrame.contentWindow.postMessage(receivedMessage, '*');
                break;
        }
    };

    socket.onclose = function (event) {
        console.log('WebSocket connection closed:', event);
    };

    //JSON 형식인지 확인하기
    function isJSON(str) {
        return /^[\],:{}\s]*$/.test(str.replace(/\\["\\\/bfnrtu]/g, '@').replace(/"[^"\\\n\r]*"|true|false|null|-?\d+(?:\.\d*)?(?:[eE][+\-]?\d+)?/g, ']').replace(/(?:^|:|,)(?:\s*\[)+/g, ''));
    }

    window.addEventListener("message", function (message) {
        if (message.data instanceof Object) {
            sendMessageToServer(message.data);
        }
    });
};

//비밀번호 찾기 팝업 닫기 함수
function onResetPwdCancel(){
    document.querySelector('.resetPwdPopup').style.display = 'none';
    document.querySelector('#parent_overlay').style.display = 'none';
}

//알럿 닫기
function onAlertClick(){
    document.querySelector('.alertPopup').style.display = 'none';
    if(document.querySelector('.resetPwdPopup').style.display != 'none'){
        document.querySelector('#resetPwd_overlay').style.display = 'none';
    } else {
        document.querySelector('#parent_overlay').style.display = 'none';
    }
}

//비밀번호 저장 함수
function onSavePwd() {
    const userId = document.querySelector('#resetPwd_id').value;
    const email = document.querySelector('#resetPwd_email').value;
    const resetPwd = document.querySelector('#resetPwd_newPwd').value;
    const resetPwdChk = document.querySelector('#resetPwd_newPwdChk').value;
    if(resetPwd == ''){
        resetPwdAlertPopup("비밀번호를 입력해주세요.");
        return;
    }
    if(resetPwdChk == ''){
        resetPwdAlertPopup("비밀번호 확인을 입력해주세요.");
        return;
    }
    if(resetPwd != resetPwdChk){
        resetPwdAlertPopup("입력해주신 비밀번호와 비밀번호 확인이 다릅니다. <br/> 확인 후 다시 시도해주세요.");
        return;
    }
    sendMessageToServer({type: 'savePwd',
        userId: userId,
        email : email,
        password : resetPwd
    });
}

//비밀번호 찾기 함수
function onResetPwd(){
    const userId = document.querySelector('#resetPwd_id').value;
    const email = document.querySelector('#resetPwd_email').value;
    if(userId == ''){
        resetPwdAlertPopup("찾을 아이디를 입력해주세요.");
        return;
    }
    if(email == ''){
        resetPwdAlertPopup("가입하실때 입력하신 이메일을 입력해주세요.");
        return;
    }
    sendMessageToServer({type: 'idChk',
        userId: userId,
        email : email,
    });
}

function resetPwdAlertPopup(message){
    if(document.querySelector('.resetPwdPopup').style.display != 'none'){
        document.querySelector('.resetPwdPopup').style.display = 'none';
        document.querySelector('#parent_overlay').style.display = 'none';
    }
    document.querySelector('.alertPopup_text').innerHTML = message;
    document.querySelector('.alertPopup').style.display = '';
    document.querySelector('#resetPwd_overlay').style.display = 'block';
}

function alertPopup(message){
    document.querySelector('.alertPopup_text').innerHTML = message;
    document.querySelector('.alertPopup').style.display = '';
    document.querySelector('#parent_overlay').style.display = 'block';
}

// 이벤트 발생 시 서버로 메시지 전송
function sendMessageToServer(message) {
    socket.send(JSON.stringify(message));
}