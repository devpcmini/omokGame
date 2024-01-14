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

const audio = new Audio('../audio/stoneSound.wav');

//parent에서 message 받는 이벤트 리스너 
window.addEventListener("message", function (message) {
    if (message.data instanceof Object) {
        const receivedMessage = message.data;
        const blackDiv = document.querySelector('.gameParticipants_blackColor');
        const whiteDiv = document.querySelector('.gameParticipants_whiteColor');
        switch (receivedMessage.type) {
            case 'start' :
                isStart = true;
                document.querySelector('.gameStart_button').disabled = true;
                document.querySelector('.board').style.cursor = 'pointer';
                loadOmokBoard();
                break;
            case 'end' : //게임 종료
                GameEndScreen(receivedMessage.data);
                break;
            case 'roomList' : //방 목록 조회
                createRoomList({ roomList: receivedMessage.data.map(room => ({ name: room.name }))});
                break;
            case 'joinRoom' : //방 입장
                isFirst = true;
                broadMessage = [];
                publicRoom = [receivedMessage.data.name,
                    receivedMessage.data.blackPlayer == null ? "" : receivedMessage.data.blackPlayer,
                    receivedMessage.data.whitePlayer == null ? "" : receivedMessage.data.whitePlayer,
                    receivedMessage.data.takes == null ? [] : receivedMessage.data.takes
                ];
                createGameParticipants(publicRoom[0], publicRoom[1], publicRoom[2]);
                break;
            case 'message' : //메세지 받기
                broadMessage.push(receivedMessage.data);
                if(isFirst && receivedMessage.data.indexOf('[입장] => ') > -1){
                    isFirst = false;
                    myId = receivedMessage.data.replace("[입장] => ","");
                }
                createGameParticipants(publicRoom[0], publicRoom[1], publicRoom[2]);
                break;
            case 'player_select' : //해당 player turn 지정
                myTurn = true;
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
        }
    }
});

//parent한테 메시지 보내는 함수
function parentToMessage(message){
    window.parent.postMessage(message);
}

//message 이벤트를 받아 쌓는 함수
function messageLine(msg) {
    return msg + "<br />";
}

//방 나가기 버튼 클릭 시 발생 함수
function leaveRoom(){
    document.querySelector('.exitPopup').style.display = '';
}

//관전하기 버튼 클릭 시 발생 함수
function moveViewer() {
    parentToMessage({type: 'changeRole', data: 'viewer'});
}

//방 만들기 버튼 클릭 시 발생 함수
function handleNewRoom(event){
    event.preventDefault();
    const name = event.target.roomName.value;
    event.target.roomName.value = "";
    if (name.length === 0) {
        window.parent.document.querySelector('.alertPopup_text').innerText = '방 이름을 입력해주세요.';
        window.parent.document.querySelector('.alertPopup').style.display = '';
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
    const startBtnDisabled = !((blackPlayer !== '' && whitePlayer !== '') && myId === blackPlayer);
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
          <p>${broadMessage.map(messageLine).join("")}</p>
        </div>
        <button class="gameStart_button" ${startBtnDisabled === true ? `disabled="true"` : ``} onclick="gameStart()">게임시작</button>
      </div>
      <div class="gameParticipants_buttons">
        <button class="gameParticipants_button" onclick="moveViewer()">관전하기</button>
        <button class="gameParticipants_button" onclick="leaveRoom()">방 나가기</button>
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
    myTurn = false;
    if (publicRoom[3].find((take) => take.x === thisCoord.x && take.y === thisCoord.y) !== undefined) {
        return;
    } else {
        coord = thisCoord;
    }
    audio.play();
    parentToMessage({type: 'move', data : coord});
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
    document.querySelector('.endPopup').style.display = '';
    document.querySelector('.endPopup_text').innerText = text;
}

//게임 승자 팝업 닫기 함수
function onGameEnd(){
    isStart = false;
    document.querySelector('.endPopup').style.display = 'none';
}

//게임 시작 함수
function onGameStart(){
    document.querySelector('.startPopup').style.display = 'none';
    parentToMessage({type: 'start'});
}

//게임 시작 팝업 취소 함수
function onGameStartWait(){
    document.querySelector('.startPopup').style.display = 'none';
}

//방 나가기 함수
function onGameExit(){
    document.querySelector('.exitPopup').style.display = 'none';
    parentToMessage({type: 'leaveRoom'});
}
//방 나가기 취소 함수
function onGameExitCancel(){
    document.querySelector('.exitPopup').style.display = 'none';
}

//게임시작 팝업 띄우기
function gameStart(){
    if(publicRoom[1] !== '' && publicRoom[2] !== '') {
        if(myId !== publicRoom[1]){
            return;
        }
        document.querySelector('.startPopup').style.display = '';
    }
}