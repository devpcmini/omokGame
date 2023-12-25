let roomName;
let nickName;

window.addEventListener("message", function (message) {
    if (message.data instanceof Object) {
        const receivedMessage = message.data;
        switch (receivedMessage.type) {
            case 'placingStone' :
                const stone = document.createElement('div');
                stone.className = 'omok-stone ' + receivedMessage.color + ' ' + seq++;
                stone.style.top = receivedMessage.row * 40 + 20 + 'px';
                stone.style.left = receivedMessage.col * 40 + 20 + 'px';
                stone.setAttribute("data-row", receivedMessage.row);
                stone.setAttribute("data-col", receivedMessage.col);
                document.querySelector('.omok-board').appendChild(stone);
                checkWin();
                break;
            case 'winner' :
                winner = receivedMessage.color;
                alert(winner + ' wins!');
                break;
            case 'create' :
                const roomNameEle = document.querySelector('#roomName');
                roomName =receivedMessage.roomName;
                roomNameEle.textContent = 'room Name : ' + roomName;
                nickName = receivedMessage.nickname;
                if(receivedMessage.stoneColor == "black"){
                    document.querySelector('.player1').textContent = nickName;
                } else if(receivedMessage.stoneColor == "white") {
                    document.querySelector('.player2').textContent = nickName;
                }
                userCheck();
                break;
            case 'join' :
                nickName = receivedMessage.nickname;
                if(document.querySelector('.player1').textContent != 'empty user'){
                    document.querySelector('.player2').textContent = nickName;
                } else {
                    document.querySelector('.player1').textContent = nickName;
                }
                break;
        }
    }
});

function ParentToMessage(message){
    window.parent.postMessage(message);
}

let seq = 0;
let winner;
// dot 클릭 이벤트
function handleDotClick(e) {
    if(winner){
        return;
    }
    const row = e.getAttribute("data-row");
    const col = e.getAttribute("data-col");
    const message = {type: 'placingStone', color: seq%2==0 ? 'black' : 'white' , row:row , col:col };
    ParentToMessage(message);
}

//가로,세로,대각선, 역대각선으로 다섯 개 이상의 돌이 연속되어 있는지 확인
function checkWin() {
    const board = document.querySelector('.omok-board');
    const stones = Array.from(board.querySelectorAll('.omok-stone'));

    for (const stone of stones) {
        const stoneColor = stone.classList.contains('black') ? 'black' : 'white';
        const stonePosition = {
            top: parseInt(stone.style.top),
            left: parseInt(stone.style.left)
        };

        if (checkDirection(board, stoneColor, stonePosition, 1, 0) || // 가로
            checkDirection(board, stoneColor, stonePosition, 0, 1) || // 세로
            checkDirection(board, stoneColor, stonePosition, 1, 1) || // 대각선
            checkDirection(board, stoneColor, stonePosition, 1, -1)) {  // 역대각선
            const message = {type: 'winner', color: stoneColor };
            ParentToMessage(message);
            return;
        }
    }
}

// 특정 위치에서 주어진 방향으로 가로,세로,대각선, 역대각선으로 다섯 개 이상의 돌이 연속되어 있는지 확인
function checkDirection(board, color, position, directionX, directionY) {
    const consecutiveStones = [position];
    for (let i = 1; i < 5; i++) {
        const nextPosition = {
            top: position.top + i * directionY * 40,
            left: position.left + i * directionX * 40
        };
        const nextStone = findStoneAtPosition(board, nextPosition);
        if (nextStone && nextStone.classList.contains(color)) {
            consecutiveStones.push(nextPosition);
        } else {
            break;
        }
    }
    for (let i = 1; i < 5; i++) {
        const prevPosition = {
            top: position.top - i * directionY * 40,
            left: position.left - i * directionX * 40
        };
        const prevStone = findStoneAtPosition(board, prevPosition);
        if (prevStone && prevStone.classList.contains(color)) {
            consecutiveStones.push(prevPosition);
        } else {
            break;
        }
    }
    if (consecutiveStones.length >= 5) {
        return true;
    }
    return false;
}

// 주어진 위치에 있는 돌을 찾아 반환
function findStoneAtPosition(board, position) {
    const stones = Array.from(board.querySelectorAll('.omok-stone'));
    return stones.find(stone => {
        const stonePosition = {
            top: parseInt(stone.style.top),
            left: parseInt(stone.style.left)
        };
        return stonePosition.top === position.top && stonePosition.left === position.left;
    });
}

function stoneColorSwitch(e){
    let target;
    if(e.id == "player1"){
        document.querySelector('.player1').textContent = nickName;
        document.querySelector('.player2').textContent = 'empty user';
    } else {
        document.querySelector('.player1').textContent = 'empty user';
        document.querySelector('.player2').textContent = nickName;
    }
    userCheck();
}

function userCheck(){
    if(document.querySelector('.player1').textContent != 'empty user'){
        document.querySelector('#player1').style.display = 'none';
    } else {
        document.querySelector('#player1').style.display = '';
    }
    if(document.querySelector('.player2').textContent != 'empty user'){
        document.querySelector('#player2').style.display = 'none';
    } else {
        document.querySelector('#player2').style.display = '';
    }
}