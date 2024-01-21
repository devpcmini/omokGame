const OFFSET = 3.62;
const SPACE = 5.14;
let inBoard = false;
let myTurn = false;
let coord = {};
let publicRoom = [];
let broadMessage = [];
let isStart = false;
let isFirst = true;
let myId;
let roomName;
let joinMessage;

const audio = new Audio('../audio/stoneSound.wav');

//parent에서 message 받는 이벤트 리스너 
window.addEventListener("message", function (message) {
    if (message.data instanceof Object) {
        const receivedMessage = message.data;
        console.log(receivedMessage);
        const blackDiv = document.querySelector('.gameParticipants_blackColor');
        const whiteDiv = document.querySelector('.gameParticipants_whiteColor');
        switch (receivedMessage.type) {
            case 'login' :
                if(receivedMessage.data.indexOf('입력한 정보가 올바르지 않습니다.') > -1){
                    alertPopup(receivedMessage.data);
                } else {
                    rememberId();
                    document.getElementById("signInForm").reset();
                    document.getElementById("signUpForm").reset();
                    document.querySelector('#login').style.display = 'none';
                    document.querySelector('#waitingRoom').style.display = '';
                }
                break;
            case 'signUp' :
                alertPopup(receivedMessage.data);
                if(receivedMessage.data.indexOf('회원가입되었습니다') > -1){
                    const radioTab = document.getElementsByName('tab');
                    // 로그인 폼으로 이동
                    radioTab[0].checked = true;
                } else {
                    document.getElementById("signUpForm").reset();
                }
                break;
            case 'error' :
                alertPopup(receivedMessage.data);
                break;
            case 'start' :
                isStart = true;
                document.querySelector('.gameStart_button').disabled = true;
                document.querySelector('#moveViewer').disabled = true;
                document.querySelector('#inviteUser').disabled = true;
                document.querySelector('#leaveRoom').disabled = true;
                if(myId == document.querySelectorAll('.gameParticipants_playername')[1].innerText) {
                    document.querySelector('#giveUp').disabled = true;
                    document.querySelector('#undoMove').disabled = true;
                }
                document.querySelector('.board').style.cursor = 'pointer';
                loadOmokBoard();
                break;
            case 'end' : //게임 종료
                document.querySelector('#moveViewer').disabled = false;
                document.querySelector('#inviteUser').disabled = false;
                document.querySelector('#leaveRoom').disabled = false;
                publicRoom = [roomName,"","",[]];
                GameEndScreen(receivedMessage.data);
                break;
            case 'roomList' : //방 목록 조회
                createRoomList({ roomList: receivedMessage.data.map(room => ({ name: room.name }))});
                break;
            case 'joinRoom' : //방 입장
                isFirst = true;
                broadMessage = [];
                roomName = receivedMessage.data.name;
                publicRoom = [receivedMessage.data.name,
                    receivedMessage.data.blackPlayer,
                    receivedMessage.data.whitePlayer,
                    receivedMessage.data.takes
                ];
                createGameParticipants(publicRoom[0], publicRoom[1], publicRoom[2]);
                break;
            case 'message' : //메세지 받기
                broadMessage.push(receivedMessage.data);
                if(isFirst && receivedMessage.data.indexOf('[입장] => ') > -1){
                    isFirst = false;
                    myId = receivedMessage.data.replace("[입장] => ","").replace(/<\/?[^>]+(>|$)/g, "");
                }
                if(receivedMessage.data.indexOf('[쌍삼] => ') > -1){
                    myTurn = true;
                }
                joinMessage = broadMessage.map(messageLine).join("");
                createGameParticipants(publicRoom[0], publicRoom[1], publicRoom[2]);
                document.querySelector('#messages').scrollTop = document.querySelector('#messages').scrollHeight;
                break;
            case 'sendMessage' :
                if(receivedMessage.data.indexOf(myId) > -1){
                    receivedMessage.data = receivedMessage.data.replace(myId, '본인');
                }
                broadMessage.push(receivedMessage.data);
                joinMessage = broadMessage.map(messageLine).join("");
                createGameParticipants(publicRoom[0], publicRoom[1], publicRoom[2]);
                document.querySelector('#messages').scrollTop = document.querySelector('#messages').scrollHeight;
                break;
            case 'player_select' : //해당 player turn 지정
                myTurn = true;
                document.querySelector('#giveUp').disabled = false;
                document.querySelector('#undoMove').disabled = false;
                loadOmokBoard();
                break;
            case 'changeRole' :
                myTurn = false;
                publicRoom[1] = receivedMessage.data.blackPlayer;
                publicRoom[2] = receivedMessage.data.whitePlayer;
                if (publicRoom[1] !== "" &&
                    publicRoom[2] !== "") {
                    publicRoom[3] = [];
                }
                loadOmokBoard();
                createGameParticipants(publicRoom[0], publicRoom[1], publicRoom[2]);
                break;
            case 'move' : //오목판 reload
                publicRoom[3].push(receivedMessage.data);
                const color = publicRoom[3].length % 2 === 0 ? 'black' : 'white';
                if(color === 'black') {
                    blackDiv.classList.add('blink_turn');
                    if(whiteDiv.classList.contains('blink_turn')) {
                        whiteDiv.classList.remove('blink_turn');
                    }
                } else if(color === 'white'){
                    whiteDiv.classList.add('blink_turn');
                    if(blackDiv.classList.contains('blink_turn')) {
                        blackDiv.classList.remove('blink_turn');
                    }
                }
                loadOmokBoard();
                break;
            case 'undoMove' :
                publicRoom[3] = receivedMessage.data;
                loadOmokBoard();
                break;
            case 'availUsers' :
                invitePopSetting(receivedMessage.data);
                setVisible('.invitePopup');
                break;
            case 'invite' :
                document.querySelector('.acceptPopup_text').innerText = receivedMessage.data;
                setVisible('.acceptPopup');
                break;
        }
    }
});

//parent한테 메시지 보내는 함수
function parentToMessage(message){
    window.parent.postMessage(message);
}

//message 이벤트를 받아 쌓는 함수
function messageLine(msg) {
    return `${msg}<br />`;
}

//방 나가기 버튼 클릭 시 발생 함수
function leaveRoom(){
    if(isStart){
        return;
    }
    setVisible('.exitPopup');
}

//관전하기 버튼 클릭 시 발생 함수
function moveViewer() {
    parentToMessage({type: 'changeRole', data: 'viewer'});
}

//초대하기 버튼 클릭 시 발생 함수
function inviteUser() {
    if(isStart){
        return;
    }
    parentToMessage({type: 'availUsers'});
}

//항복하기 버튼 클릭 시 발생 함수
function giveUp(){
    if(!myTurn || !isStart){
        return;
    }
    setVisible('.giveUpPopup');
}

//무르기 버튼 클릭 시 발생 함수
function undoMove(){
    if(!myTurn || publicRoom[3].length < 2){
        return;
    }
    setVisible('.undoMovePopup');
}

//방 만들기 버튼 클릭 시 발생 함수
function handleNewRoom(event){
    event.preventDefault();
    const name = event.target.roomName.value;
    event.target.roomName.value = "";
    if (name.length === 0) {
        alertPopup("방 이름을 입력해주세요.");
        return;
    }
    const joinRoomMessage = {type: 'createRoom', name: name};
    parentToMessage(joinRoomMessage);
}

//방 목록 생성 함수
function createRoomList(props) {
    const roomList = props.roomList;
    const ul = document.querySelector('#roomList_container');
    ul.innerHTML = '';
    roomList.forEach(function (roomItem) {
        const li = roomInfo(roomItem);
        ul.appendChild(li);
    });
}

//방 요소 생성 함수
function roomInfo(room) {
    const handleEnterRoom = () => {
        const joinRoomMessage = {type: 'joinRoom', name: room.name};
        parentToMessage(joinRoomMessage);
    };

    const li = document.createElement('li');
    li.className = 'roomList_item';

    const p = document.createElement('p');
    p.className = 'roomList_name';
    p.textContent = room.name;

    const button = document.createElement('button');
    button.className = 'roomList_enter';
    button.textContent = '입장하기';
    button.addEventListener('click', handleEnterRoom);

    li.appendChild(p);
    li.appendChild(button);

    return li;
}

//방 입장 시 오목판 우측에 요소 생성 함수
function createGameParticipants(roomName, blackPlayer, whitePlayer) {
    const gamePanel = document.querySelector(".gameParticipants");
    const startBtnDisabled = !((blackPlayer !== '' && whitePlayer !== '') && myId === blackPlayer) || isStart;
    gamePanel.innerHTML = `
      <div class="gameParticipants_main">
        <h3 class="gameParticipants_title">${roomName}</h3>
        <div class="gameParticipants_players">
          <div class="gameParticipants_player">
            <h4 class="gameParticipants_playercolor gameParticipants_blackColor">
              Black
            </h4>
            <div class="gameParticipants_playerinfo">
              ${
                blackPlayer === ""
                    ? `<button class="gameParticipants_join" onclick="joinBlackPlayer()">참가</button>`
                    : `<p class="gameParticipants_playername">${blackPlayer}</p>`
                }
            </div>
          </div>
          <div class="gameParticipants_player">
            <h4 class="gameParticipants_playercolor gameParticipants_whiteColor">White</h4>
            <div class="gameParticipants_playerinfo">
              ${
                  whitePlayer === ""
                      ? `<button class="gameParticipants_join" onclick="joinWhitePlayer()">참가</button>`
                      : `<p class="gameParticipants_playername">${whitePlayer}</p>`
              }
            </div>
          </div>
        </div>
        <div class="gameParticipants_message">
          <p id="messages">${joinMessage}</p>
        </div>
        <div class="gameParticipants_sendForm">
          <input type="text" id="messageInput" placeholder="메시지를 입력하세요." onkeyup="messageInputKeyup(event)">
          <button id="sendMessage" onclick="sendMessage()">전송</button>
        </div>
        <div class="gameParticipants_buttons">
            <button class="gameStart_button" ${startBtnDisabled === true ? `disabled="true"` : ``} style="width: 80%" onclick="gameStart()">게임시작</button>
            <button class="gameParticipants_button" id="moveViewer" style="width: 17%" onclick="moveViewer()">관전하기</button>
        </div>
      </div>
      <div style="width: 100%">
          <div class="gameParticipants_buttons">
            <button class="gameParticipants_button" id="giveUp" onclick="giveUp()">항복</button>
            <button class="gameParticipants_button" id="undoMove" onclick="undoMove()">무르기</button>
          </div>
          <div class="gameParticipants_buttons">
            <button class="gameParticipants_button" id="inviteUser" onclick="inviteUser()">초대하기</button>
            <button class="gameParticipants_button" id="leaveRoom" onclick="leaveRoom()">방 나가기</button>
          </div>
      </div>
    `;
}

//흑돌 플레이어로 참가
function joinBlackPlayer() {
    parentToMessage({type: 'changeRole', data : 'black'});
}

//백돌 플레이어로 참가
function joinWhitePlayer() {
    parentToMessage({type: 'changeRole', data : 'white'});
}

//오목판 안에 마우스 들어왔을때 이벤트
function handleBoardEnter() {
    inBoard = true;
}

//오목판 안에 마우스 나갔을때 이벤트
function handleBoardLeave() {
    inBoard = false;
}

//오목판 클릭 시 이벤트
function handleBoardClick(thisCoord) {
    if (isStart) {
        myTurn = false;
        if (publicRoom[3].find((take) => take.x === thisCoord.x && take.y === thisCoord.y) !== undefined) {
            return;
        } else {
            coord = thisCoord;
        }
        audio.play();
        parentToMessage({type: 'move', data: coord});
        document.querySelector('#giveUp').disabled = true;
        document.querySelector('#undoMove').disabled = true;
    }
}

//오목판 Load 함수
function loadOmokBoard() {
    const boardDiv = document.querySelector(".board");
    boardDiv.innerHTML = '';
    if(isStart && myTurn) {
        boardDiv.appendChild(handleStoneEvent(handleBoardEnter,handleBoardLeave,handleBoardClick));
    }
    publicRoom[3].map((takes, index) => {
        boardDiv.appendChild(createStone(index % 2 === 0 ? "black" : "white",takes.x, takes.y));
    });
    if(publicRoom[3].length > 0){
        boardDiv.appendChild(createStone("prev",publicRoom[3][publicRoom[3].length-1].x, publicRoom[3][publicRoom[3].length-1].y));
    }
}

//오목돌 생성 함수
function createStone(type, x, y ) {
    let classType = "";
    switch (type) {
        case "black":
            classType += " stone_black";
            break;
        case "white":
            classType += " stone_white";
            break;
        case "prev":
            classType += " stone_prev";
            break;
    }

    const stoneElement = document.createElement("div");
    stoneElement.className = `stone ${classType}`;
    stoneElement.style.left = `${x * SPACE + OFFSET}%`;
    stoneElement.style.top = `${y * SPACE + OFFSET}%`;
    stoneElement.setAttribute("key", `${x}${y}`);
    return stoneElement;
}

//오목돌 놓는 좌표 구하는 함수
function getCoord(event) {
    let stoneX;
    let stoneY;

    const percentX = (event.offsetX * 100.0) / event.target.clientWidth;
    const percentY = (event.offsetY * 100.0) / event.target.clientHeight;

    stoneX = parseInt((percentX - OFFSET) / SPACE + 0.5);
    stoneY = parseInt((percentY - OFFSET) / SPACE + 0.5);

    if (stoneX < 0) {
        stoneX = 0;
    }
    if (stoneY < 0) {
        stoneY = 0;
    }
    if (stoneX > 18) {
        stoneX = 18;
    }
    if (stoneY > 18) {
        stoneY = 18;
    }

    return {
        x: stoneX,
        y: stoneY,
    };
}

//오목판에 이벤트 등록 함수
function handleStoneEvent(onBoardEnter,onBoardLeave,onBoardClick) {
    function onMouseEnter() {
        onBoardEnter();
    }
    function onMouseLeave() {
        onBoardLeave();
    }
    function onMouseClick(event) {
        onBoardClick(getCoord(event));
    }
    const coordDiv = document.createElement('div');
    coordDiv.className = "coord";
    coordDiv.addEventListener("mouseenter", onMouseEnter);
    coordDiv.addEventListener("mouseleave", onMouseLeave);
    coordDiv.addEventListener("click", onMouseClick);

    return coordDiv;
}

//게임 승자 발생 시 팝업 호출 함수
function GameEndScreen(winner){
    const text = `${winner} is winner!`;
    setVisible('.endPopup');
    document.querySelector('.endPopup_text').innerText = text;
}

//게임 승자 팝업 닫기 함수
function onGameEnd(){
    isStart = false;
    setInvisible('.endPopup');
}

//게임 시작 함수
function onGameStart(){
    setInvisible('.startPopup');
    parentToMessage({type: 'start'});
}

//게임 시작 팝업 취소 함수
function onGameStartWait(){
    setInvisible('.startPopup');
}

//방 나가기 함수
function onGameExit(){
    setInvisible('.exitPopup');
    parentToMessage({type: 'leaveRoom'});
}
//방 나가기 취소 함수
function onGameExitCancel(){
    setInvisible('.exitPopup');
}

//항복 함수
function onGiveUp(){
    setInvisible('.giveUpPopup');
    const color = myId == document.querySelectorAll('.gameParticipants_playername')[0].innerText ? 'black' : 'white';
    parentToMessage({type: 'giveUp', color: color});
}

//항복 취소 함수
function onGiveUpCancel(){
    setInvisible('.giveUpPopup');
}

//항복 함수
function onUndoMove(){
    setInvisible('.undoMovePopup');
    parentToMessage({type: 'undoMove'});
}

//항복 취소 함수
function onUndoMoveCancel(){
    setInvisible('.undoMovePopup');
}

//초대하기 함수
function onInvite(){
    setInvisible('.invitePopup');
    parentToMessage({type: 'invite', data : document.querySelector('.invitePopup_user.clicked').innerText});
}

//초대하기 취소 함수
function onInviteCancel(){
    setInvisible('.invitePopup');
}

//초대 수락 함수
function onAccept(){
    setInvisible('.acceptPopup');
    const joinRoomMessage = {type: 'joinRoom',
        name: findRoomName(document.querySelector('.acceptPopup_text').innerText)};
    parentToMessage(joinRoomMessage);
}

//초대 거절 함수
function onReject(){
    setInvisible('.acceptPopup');
}

//게임시작 팝업 띄우기
function gameStart(){
    if(publicRoom[1] !== '' && publicRoom[2] !== '') {
        if(myId !== publicRoom[1]){
            return;
        }
        setVisible('.startPopup');
    }
}

//팝업 닫기 및 뒷 배경을 덮는 모달 제거
function setInvisible(div){
    document.querySelector(div).style.display = 'none';
    if(div == '.acceptPopup'){
        document.getElementById('waitingRoom_overlay').style.display = 'none';
    } else {
        document.getElementById('gamingRoom_overlay').style.display = 'none';
    }
}

//팝업 띄우기 및 뒷 배경을 덮는 모달 표출
function setVisible(div){
    document.querySelector(div).style.display = '';
    if(div == '.acceptPopup'){
        document.getElementById('waitingRoom_overlay').style.display = 'block';
    } else {
        document.getElementById('gamingRoom_overlay').style.display = 'block';
    }
}

//유저 채팅 메세지 전송
function sendMessage(){
    const messageInput = document.querySelector('#messageInput');
    if(messageInput.value == ''){
        alertPopup("메시지를 입력해주세요.");
        return;
    }
    parentToMessage({type: 'sendMessage', message: messageInput.value});
    messageInput.value = '';
}

//초대가능한 유저 팝업에 생성
function invitePopSetting(data){
    document.querySelector('.invitePopup_userList').innerHTML = '';
    const dataSplit = data.split(',');
    for(let i=0; i<dataSplit.length; i++) {
        const temp = document.createElement('div');
        temp.className = 'invitePopup_user';
        temp.textContent = dataSplit[i];

        temp.addEventListener('click', () => {
            const allDivs = document.querySelectorAll('.invitePopup_user');
            allDivs.forEach(div => div.classList.remove('clicked'));
            temp.classList.add('clicked');
        });
        document.querySelector('.invitePopup_userList').appendChild(temp);
    }
}

//문자열에서 ''(싱글쿼터) 안에 있는 문자 정규식으로 찾기
function findRoomName(inputString) {
    const regex = /'([^']*)'/g;
    const matches = [];

    let match;
    while ((match = regex.exec(inputString)) !== null) {
        matches.push(match[1]);
    }

    return matches.toString();
}

window.onload = function() {
    const rememberId = getCookie('saveid');
    if(rememberId != ""){
        document.querySelector('#signInCheck').checked = true;
        document.querySelector('#signInUser').value = rememberId;
    }
    // 이벤트 리스너 등록
    document.getElementById('signInForm').addEventListener('submit', (event)=>{
        event.preventDefault(); // 폼 제출 기본 동작 막기
        onLogin(); // 로그인 함수 호출
    });

    document.getElementById('signUpForm').addEventListener('submit', (event)=>{
        event.preventDefault(); // 폼 제출 기본 동작 막기
        onSignUp(); // 회원가입 함수 호출
    });

    document.querySelector('.newRoom_input').addEventListener("keyup",(event)=>{
       console.log(event);
    });
}

function messageInputKeyup(event){
   console.log(event);
}

function onLogin(){
    const signInUser = document.querySelector('#signInUser').value;
    const signInPass = document.querySelector('#signInPass').value;
    if(signInUser == ''){
        alertPopup("아이디를 입력해주세요.");
        return;
    }
    if(signInPass == ''){
        alertPopup("비밀번호를 입력해주세요.");
        return;
    }
    const joinRoomMessage = {type: 'login',
        userId: signInUser,
        password : signInPass
    };
    parentToMessage(joinRoomMessage);
}
function onSignUp(){
    console.log('회원가입 버튼이 클릭되었습니다.');
    const signUpUserId = document.querySelector('#signUpUser').value;
    const signUpPass = document.querySelector('#signUpPass').value;
    const signUpRepeatPass = document.querySelector('#signUpRepeatPass').value;
    const signUpEmail = document.querySelector('#signUpEmail').value;
    if(signUpUserId == ''){
        alertPopup("아이디를 입력해주세요.");
        return;
    }
    if(signUpPass == ''){
        alertPopup("비밀번호를 입력해주세요.");
        return;
    }
    if(signUpRepeatPass == ''){
        alertPopup("비밀번호 확인을 입력해주세요.");
        return;
    }
    if(signUpEmail == ''){
        alertPopup("이메일 주소를 입력해주세요.");
        return;
    }
    if(document.querySelector('#signUpPass').value != document.querySelector('#signUpRepeatPass').value){
        alertPopup("입력하신 비밀번호가 다릅니다.");
        return;
    }
    const joinRoomMessage = {type: 'signUp',
        userId: signUpUserId,
        password : signUpPass,
        email : signUpEmail,
    };
    parentToMessage(joinRoomMessage);
}

function alertPopup(message){
    window.parent.document.querySelector('.alertPopup_text').innerText = message;
    window.parent.document.querySelector('.alertPopup').style.display = '';
    window.parent.document.querySelector('#parent_overlay').style.display = 'block';
}

function setCookie(name, id, expiryDays) {
    const todayDate = new Date();
    todayDate.setTime(todayDate.getTime() + 0);
    if(todayDate > expiryDays){
        document.cookie = name + "=" + escape(id) + "; path=/; expires=" + expiryDays + ";";
    }else if(todayDate < expiryDays){
        todayDate.setDate(todayDate.getDate() + expiryDays);
        document.cookie = name + "=" + escape(id) + "; path=/; expires=" + todayDate.toGMTString() + ";";
    }
    console.log(document.cookie);
}

function getCookie(Name) {
    const search = Name + "=";

    if (document.cookie.length > 0) { // 쿠키가 설정되어 있다면
        let offset = document.cookie.indexOf(search);
        if (offset != -1) { // 쿠키가 존재하면
            offset += search.length;
            let end = document.cookie.indexOf(";", offset);
            console.log("end : " + end);
            // 쿠키 값의 마지막 위치 인덱스 번호 설정
            if (end == -1) {
                end = document.cookie.length;
            }
            console.log("end위치  : " + end);

            return unescape(document.cookie.substring(offset, end));
        }
    }
    return "";
}

function rememberId(){
    const time = new Date();
    if (document.querySelector('#signInCheck').checked){
        time.setTime(time.getTime() + 1000 * 3600 * 24 * 30);
        setCookie("saveid",document.querySelector('#signInUser').value, time);
    }else{
        time.setTime(time.getTime() - 1000 * 3600 * 24 * 30);
        setCookie("saveid", document.querySelector('#signInUser').value, time);
    }
}

