let socket;
let messaging;
let childFrame;

function getFCMToken() {
    messaging.getToken().then(function (currentToken) {
        console.log('현재 토큰: ' + currentToken);
        if (currentToken) {
            childFrame.contentWindow.document.querySelector('#token').value = currentToken;
            socket.send(JSON.stringify({type: 'connect', token: currentToken}));
            console.log('토큰 가져오기 성공 - ' + currentToken);
        } else {
            console.log('토큰이 없습니다. 권한을 요청하여 생성하세요.');
        }
    }).catch(function (err) {
        console.log('토큰을 가져오는 중 오류 발생. ', err);
    });
}

function checkWebSocketAndToken() {
    if (socket.readyState === WebSocket.OPEN && childFrame.contentWindow.document.querySelector('#token').value != "") {
        if(document.querySelector('#isPop').value != 'true') {
            document.querySelector('#parent_overlay').style.display = 'none';
        }
        document.getElementById('spinner').style.display = 'none';
        document.getElementById('progress-text').style.display = 'none';
    } else {
        document.querySelector('#parent_overlay').style.display = 'block';
        document.getElementById('spinner').style.display = 'block';
        document.getElementById('progress-text').innerText = '로딩 중...';
    }
}

function forceRefreshToken() {
    messaging.getToken().then(function(currentToken) {
        if (currentToken) {
            // 현재 토큰이 있다면, 서버에 등록하거나 필요한 작업을 수행할 수 있음
            console.log('현재 FCM 토큰: ' + currentToken);
            // 토큰 갱신을 트리거하려면 현재 토큰을 삭제하고
            // 앱이 다시 실행될 때나 권한이 부여될 때 새로운 토큰을 얻게 됨
            messaging.deleteToken(currentToken).then(function() {
                console.log('토큰 삭제 성공.');
            }).catch(function(err) {
                console.log('토큰 삭제 실패: ', err);
            });
        } else {
            console.log('현재 FCM 토큰이 없습니다.');
        }
    }).catch(function(err) {
        console.log('FCM 토큰을 가져오는 중 오류 발생 ', err);
    });
}

window.onload = function() {
    childFrame = document.getElementById('frameElement');
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

    messaging = firebase.messaging();
    const publicVapidKey = 'BB7-hEbajhQngV5x-LK5PZiTY3noPyum8AsmzDF2AC3Us7hAFq7tiGwVHPyG-1JsTZRRj5wAE5xSfEE46TRYUq0';
    messaging.usePublicVapidKey(publicVapidKey);
    // FCM 토큰을 가져오는 함수

    // 초기 FCM 토큰을 가져옴
    getFCMToken();
    setInterval(checkWebSocketAndToken, 1000);

    socket = new WebSocket('ws://' + ip + ':8080/ws');
    socket.onopen = function (event) {
        console.log("open socket")
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
                    document.querySelector('#logout').style.display = 'flex';
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
        forceRefreshToken();
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

    document.querySelector('#logout').addEventListener('click',()=>{
        location.reload(true);
    });
};

//비밀번호 찾기 팝업 닫기 함수
function onResetPwdCancel(){
    document.querySelector('.resetPwdPopup').style.display = 'none';
    document.querySelector('#parent_overlay').style.display = 'none';
    document.querySelector('#isPop').value = false;
}

//알럿 닫기
function onAlertClick(){
    document.querySelector('.alertPopup').style.display = 'none';
    if(document.querySelector('.resetPwdPopup').style.display != 'none'){
        document.querySelector('#resetPwd_overlay').style.display = 'none';
    } else {
        document.querySelector('#parent_overlay').style.display = 'none';
        document.querySelector('#isPop').value = false;
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
        document.querySelector('#isPop').value = false;
    }
    document.querySelector('.alertPopup_text').innerHTML = message;
    document.querySelector('.alertPopup').style.display = '';
    document.querySelector('#resetPwd_overlay').style.display = 'block';
}

function alertPopup(message){
    document.querySelector('.alertPopup_text').innerHTML = message;
    document.querySelector('.alertPopup').style.display = '';
    document.querySelector('#parent_overlay').style.display = 'block';
    document.querySelector('#isPop').value = true;
}

// 이벤트 발생 시 서버로 메시지 전송
function sendMessageToServer(message) {
    socket.send(JSON.stringify(message));
}